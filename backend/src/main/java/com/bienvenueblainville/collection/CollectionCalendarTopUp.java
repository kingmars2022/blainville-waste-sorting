package com.bienvenueblainville.collection;

import com.bienvenueblainville.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the collection calendar from running out.
 *
 * <p>The calendar used to be hand-written rows ending on a fixed date. Whoever
 * wrote them knew the date; nobody who ran the application afterwards did, and
 * the failure was silent — the home page simply starts telling every resident
 * there is no collection, which is a lie that looks like data.
 *
 * <p>So the recurring patterns live in {@code collection_schedule_rule} and
 * this materializes them into {@code collection_event} out to a rolling
 * horizon, on startup and once a day after that. Occurrences stay real rows
 * rather than being computed per request, because the admin console, the admin
 * agent and the holiday exceptions all edit individual collections — a
 * computed calendar would have nothing to edit.
 *
 * <p>Two rules keep it honest:
 * <ul>
 *   <li>It only ever generates <em>after</em> each rule's high-water mark, so
 *       an occurrence an administrator deleted is not resurrected the next
 *       morning.</li>
 *   <li>It never backfills the past. A deployment that was down for a month
 *       resumes from today; nothing reads collections that already happened.</li>
 * </ul>
 */
@Component
public class CollectionCalendarTopUp {
    private static final Logger log = LoggerFactory.getLogger(CollectionCalendarTopUp.class);

    /** fr/en/zh templates for the sentence appended to a moved collection. */
    private static final String MOVED_FR = "Collecte d\u00e9plac\u00e9e en raison de {holiday}.";
    private static final String MOVED_EN = "Collection moved because of {holiday}.";
    private static final String MOVED_ZH = "\u56e0{holiday}\u987a\u5ef6\u3002";

    private final CollectionScheduleRuleMapper rules;
    private final CollectionHolidayMapper holidays;
    private final CollectionEventMapper events;
    private final CacheManager cacheManager;
    private final Clock clock;
    private final boolean enabled;
    private final int horizonDays;

    @Autowired
    public CollectionCalendarTopUp(
            CollectionScheduleRuleMapper rules,
            CollectionHolidayMapper holidays,
            CollectionEventMapper events,
            CacheManager cacheManager,
            @Value("${app.collection.calendar.enabled:true}") boolean enabled,
            @Value("${app.collection.calendar.horizon-days:180}") int horizonDays
    ) {
        this(rules, holidays, events, cacheManager, Clock.systemDefaultZone(), enabled, horizonDays);
    }

    /**
     * The clock is a constructor argument rather than a system call inside the
     * loop so a test can ask what this does a year from now — which is the
     * only question that would have caught the calendar running out.
     */
    public CollectionCalendarTopUp(
            CollectionScheduleRuleMapper rules,
            CollectionHolidayMapper holidays,
            CollectionEventMapper events,
            CacheManager cacheManager,
            Clock clock,
            boolean enabled,
            int horizonDays
    ) {
        this.rules = rules;
        this.holidays = holidays;
        this.events = events;
        this.cacheManager = cacheManager;
        this.clock = clock;
        this.enabled = enabled;
        this.horizonDays = horizonDays;
    }

    /**
     * fixedDelay rather than cron: it runs once at startup and then daily, so a
     * fresh deployment has a full calendar before it serves its first request
     * instead of at 3am tomorrow.
     */
    @Scheduled(fixedDelayString = "${app.collection.calendar.interval-ms:86400000}")
    public void scheduledTopUp() {
        if (!enabled) {
            return;
        }
        try {
            int generated = topUp();
            if (generated > 0) {
                log.info("Extended the collection calendar with {} generated occurrences", generated);
            }
        } catch (RuntimeException e) {
            // A calendar that is short is worse tomorrow than today, never
            // fatal now. The application serves whatever the database already
            // holds and tries again on the next run.
            log.warn("Could not extend the collection calendar; will retry on the next run", e);
        }
    }

    /**
     * Exposed so tests can drive a top-up deterministically instead of waiting
     * for the schedule, in the same way as the outbox relay.
     *
     * @return how many occurrences were written
     */
    public int topUp() {
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusDays(horizonDays);
        int written = 0;

        // Loaded once per run rather than per rule: the table is small, and
        // every rule asks it the same questions.
        HolidayShift shift = HolidayShift.of(holidays.findActiveFrom(today.minusDays(1)));

        for (CollectionScheduleRule rule : rules.findActive()) {
            // Exclusive lower bound. Clamped to yesterday when the mark has
            // fallen behind, so a deployment that was down still generates
            // today's collection - the one a resident needs tonight - without
            // backfilling the weeks it missed.
            LocalDate mark = rule.generatedThrough();
            LocalDate from = mark.isBefore(today) ? today.minusDays(1) : mark;

            List<CollectionEvent> generated = new ArrayList<>();
            Set<LocalDate> taken = new LinkedHashSet<>();
            for (LocalDate date : rule.occurrencesAfter(from, horizon)) {
                HolidayShift.Shifted shifted = shift.apply(date, rule.sector());

                // A shift can push an occurrence past the horizon. It is kept:
                // the horizon bounds how far ahead this job generates, not
                // which collections are real, and dropping it would delete the
                // one collection a holiday already made surprising. The mark
                // still advances to the horizon, so the next run resumes from
                // there and the unique key makes the repeat a no-op.
                if (!taken.add(shifted.date())) {
                    // Two occurrences of one rule landing on the same day. The
                    // unique key would absorb the second silently; say so
                    // instead, because it means a shift_days as long as the
                    // rule's own cycle - data worth a human looking at.
                    log.warn("Rule {} has two occurrences on {} after holiday shifting; keeping the first",
                            rule.id(), shifted.date());
                    continue;
                }

                generated.add(new CollectionEvent(
                        null, shifted.date(), rule.sector(), rule.collectionType(), rule.binColor(),
                        shifted.explain(rule.noteFr(), CollectionHoliday::nameFr, MOVED_FR),
                        shifted.explain(rule.noteEn(), CollectionHoliday::nameEn, MOVED_EN),
                        shifted.explain(rule.noteZh(), CollectionHoliday::nameZh, MOVED_ZH),
                        rule.sourceUrl()));
            }

            if (generated.isEmpty()) {
                continue;
            }

            // Insert first, then advance the mark. A crash in between repeats
            // the insert on the next run, which the unique key turns into a
            // no-op; the other order would lose the occurrences for good.
            events.insertGenerated(generated);
            rules.advanceGeneratedThrough(rule.id(), horizon);
            written += generated.size();
        }

        if (written == 0) {
            return 0;
        }

        Optional.ofNullable(cacheManager.getCache(CacheConfig.UPCOMING_COLLECTIONS)).ifPresent(Cache::clear);
        return written;
    }
}
