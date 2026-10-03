package com.bienvenueblainville.collection;

import com.bienvenueblainville.collection.dto.CollectionHolidayRequest;
import com.bienvenueblainville.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Administering the days the city does not collect.
 *
 * <p>The part worth reading is {@link #rebuildFrom(LocalDate)}. Writing the
 * row is not enough: {@link CollectionCalendarTopUp} only ever generates
 * <em>after</em> each rule's high-water mark, so a holiday added for next
 * month would be recorded and then ignored, because the collection it is
 * supposed to move was materialized weeks ago. An administrator would see
 * their change saved and the calendar unchanged, which is worse than having no
 * screen at all.
 */
@Service
public class CollectionHolidayService {
    private final CollectionHolidayMapper holidays;
    private final CollectionEventMapper events;
    private final CollectionScheduleRuleMapper rules;
    private final CollectionCalendarTopUp topUp;
    private final CacheManager cacheManager;
    private final Clock clock;

    @Autowired
    public CollectionHolidayService(
            CollectionHolidayMapper holidays,
            CollectionEventMapper events,
            CollectionScheduleRuleMapper rules,
            CollectionCalendarTopUp topUp,
            CacheManager cacheManager
    ) {
        this(holidays, events, rules, topUp, cacheManager, Clock.systemDefaultZone());
    }

    /** The clock is injectable so a test can rebuild the calendar at a date it chooses. */
    public CollectionHolidayService(
            CollectionHolidayMapper holidays,
            CollectionEventMapper events,
            CollectionScheduleRuleMapper rules,
            CollectionCalendarTopUp topUp,
            CacheManager cacheManager,
            Clock clock
    ) {
        this.holidays = holidays;
        this.events = events;
        this.rules = rules;
        this.topUp = topUp;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    public List<CollectionHoliday> list() {
        return holidays.findAll();
    }

    @Transactional
    public CollectionHoliday create(CollectionHolidayRequest request) {
        holidays.insert(from(null, request));
        rebuildFrom(request.holidayDate());
        CollectionHoliday saved = holidays.findByDateAndSector(request.holidayDate(), request.sector());
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Holiday was not stored");
        }
        return saved;
    }

    @Transactional
    public CollectionHoliday update(Long id, CollectionHolidayRequest request) {
        CollectionHoliday existing = require(id);
        if (holidays.update(from(id, request)) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found");
        }
        // Moving a holiday invalidates both days: the one it no longer closes
        // and the one it now does. Rebuilding from the earlier covers both.
        rebuildFrom(earlier(existing.holidayDate(), request.holidayDate()));
        return require(id);
    }

    @Transactional
    public void delete(Long id) {
        CollectionHoliday existing = require(id);
        holidays.delete(id);
        // The collection it was moving has to come back to its ordinary day.
        rebuildFrom(existing.holidayDate());
    }

    /**
     * Regenerates the calendar from {@code date} so a holiday change is
     * actually reflected in it.
     *
     * <p>Two bounds keep this honest. It deletes only generated occurrences,
     * so an administrator's own hand-entered collection is not collateral
     * damage - that is what {@code generated} in {@code V16} is for. And it
     * never reaches before today: the past is not rebuilt, because nothing
     * reads it and changing it would be rewriting history.
     *
     * <p>It does override one rule deliberately: an occurrence an
     * administrator deleted inside the rebuilt window comes back. That is the
     * trade, and this is the right side of it - the administrator is asking
     * for the calendar to change, and a stale collection on a day the city is
     * closed is the exact error this table exists to prevent.
     */
    private void rebuildFrom(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = date.isBefore(today) ? today : date;

        events.deleteGeneratedFrom(from);
        rules.rewindGeneratedThrough(from.minusDays(1));
        topUp.topUp();

        // topUp only clears the cache when it wrote something. A rebuild that
        // removes an occurrence and generates nothing in its place still
        // changed what residents should see.
        Optional.ofNullable(cacheManager.getCache(CacheConfig.UPCOMING_COLLECTIONS)).ifPresent(Cache::clear);
    }

    private CollectionHoliday require(Long id) {
        CollectionHoliday holiday = holidays.findById(id);
        if (holiday == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found");
        }
        return holiday;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static CollectionHoliday from(Long id, CollectionHolidayRequest request) {
        return new CollectionHoliday(id, request.holidayDate(), request.nameFr(), request.nameEn(),
                request.nameZh(), request.shiftDays(), request.sector(), request.sourceUrl(), request.active());
    }
}
