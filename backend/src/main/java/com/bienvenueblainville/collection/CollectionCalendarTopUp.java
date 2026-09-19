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
import java.util.List;
import java.util.Optional;

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

    private final CollectionScheduleRuleMapper rules;
    private final CollectionEventMapper events;
    private final CacheManager cacheManager;
    private final Clock clock;
    private final boolean enabled;
    private final int horizonDays;

    @Autowired
    public CollectionCalendarTopUp(
            CollectionScheduleRuleMapper rules,
            CollectionEventMapper events,
            CacheManager cacheManager,
            @Value("${app.collection.calendar.enabled:true}") boolean enabled,
            @Value("${app.collection.calendar.horizon-days:180}") int horizonDays
    ) {
        this(rules, events, cacheManager, Clock.systemDefaultZone(), enabled, horizonDays);
    }

    /**
     * The clock is a constructor argument rather than a system call inside the
     * loop so a test can ask what this does a year from now — which is the
     * only question that would have caught the calendar running out.
     */
    public CollectionCalendarTopUp(
            CollectionScheduleRuleMapper rules,
            CollectionEventMapper events,
            CacheManager cacheManager,
            Clock clock,
            boolean enabled,
            int horizonDays
    ) {
        this.rules = rules;
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

        for (CollectionScheduleRule rule : rules.findActive()) {
            // Exclusive lower bound. Clamped to yesterday when the mark has
            // fallen behind, so a deployment that was down still generates
            // today's collection - the one a resident needs tonight - without
            // backfilling the weeks it missed.
            LocalDate mark = rule.generatedThrough();
            LocalDate from = mark.isBefore(today) ? today.minusDays(1) : mark;

            List<CollectionEvent> generated = rule.occurrencesAfter(from, horizon).stream()
                    .map(date -> new CollectionEvent(
                            null, date, rule.sector(), rule.collectionType(), rule.binColor(),
                            rule.noteFr(), rule.noteEn(), rule.noteZh(), rule.sourceUrl()))
                    .toList();

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
