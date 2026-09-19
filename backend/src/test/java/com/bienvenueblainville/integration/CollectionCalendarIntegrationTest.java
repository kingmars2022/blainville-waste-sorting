package com.bienvenueblainville.integration;

import com.bienvenueblainville.collection.CollectionCalendarTopUp;
import com.bienvenueblainville.collection.CollectionEvent;
import com.bienvenueblainville.collection.CollectionEventMapper;
import com.bienvenueblainville.collection.CollectionScheduleRuleMapper;
import com.bienvenueblainville.common.Sector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that would have caught the calendar running out.
 *
 * <p>Until V12 the collection calendar was hand-written rows ending on
 * 2026-10-29. Every test passed on the day it was written and would have kept
 * passing right up to the morning the home page started telling residents
 * there was no collection — because no test ever asked what the calendar looks
 * like from a date in the future.
 *
 * <p>This one does: it drives the top-up with a clock a year ahead and asserts
 * the seeded rules still produce collections for both sectors. It runs against
 * the real MySQL, on the real migrated schema, so the seeded rules themselves
 * are part of what is under test.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class CollectionCalendarIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        // The scheduled top-up is off here so the only writes are the ones
        // these tests make deliberately, at a clock they control.
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @Autowired
    private CollectionScheduleRuleMapper rules;
    @Autowired
    private CollectionEventMapper events;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private JdbcTemplate jdbc;

    private LocalDate watermark;
    private Map<Long, LocalDate> marksBefore;

    @BeforeEach
    void snapshot() {
        watermark = jdbc.queryForObject(
                "select coalesce(max(collection_date), '2000-01-01') from collection_event", LocalDate.class);
        marksBefore = rules.findActive().stream()
                .collect(Collectors.toMap(rule -> rule.id(), rule -> rule.generatedThrough()));
    }

    @AfterEach
    void restore() {
        jdbc.update("delete from collection_event where collection_date > ?", watermark);
        marksBefore.forEach((id, mark) ->
                jdbc.update("update collection_schedule_rule set generated_through = ? where id = ?", mark, id));
    }

    private CollectionCalendarTopUp topUpOn(String today) {
        Clock clock = Clock.fixed(
                ZonedDateTime.of(LocalDate.parse(today).atStartOfDay(), ZONE).toInstant(), ZONE);
        return new CollectionCalendarTopUp(rules, events, cacheManager, clock, true, 180);
    }

    @Test
    void theCalendarStillHasCollectionsAYearFromNow() {
        LocalDate aYearOut = LocalDate.now().plusYears(1);

        assertThat(events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30)))
                .as("before the top-up, the hand-written rows have long run out")
                .isEmpty();

        topUpOn(aYearOut.toString()).topUp();

        assertThat(events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30)))
                .as("north: organics citywide plus biweekly recycling")
                .isNotEmpty();
        assertThat(events.findUpcoming(Sector.south, aYearOut, aYearOut.plusDays(30)))
                .as("south: the other half of the biweekly cycle")
                .isNotEmpty();
    }

    @Test
    void everySeededRuleContributesToTheGeneratedCalendar() {
        LocalDate aYearOut = LocalDate.now().plusYears(1);

        topUpOn(aYearOut.toString()).topUp();

        List<CollectionEvent> month = events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30));

        assertThat(month).extracting(CollectionEvent::collectionType)
                .contains(com.bienvenueblainville.collection.CollectionType.organic,
                        com.bienvenueblainville.collection.CollectionType.recycling);
        assertThat(month).allSatisfy(event ->
                assertThat(event.sourceUrl()).startsWith("https://blainville.ca"));
    }

    @Test
    void runningItTwiceDoesNotDuplicateACollection() {
        LocalDate aYearOut = LocalDate.now().plusYears(1);

        topUpOn(aYearOut.toString()).topUp();
        List<CollectionEvent> afterFirst = events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30));

        // Same clock, same rules: the high-water mark should make this a no-op,
        // and the unique key should make it harmless even if it were not.
        int second = topUpOn(aYearOut.toString()).topUp();

        assertThat(second).isZero();
        assertThat(events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30)))
                .hasSameSizeAs(afterFirst);
    }

    @Test
    void aDeletedOccurrenceStaysDeleted() {
        LocalDate aYearOut = LocalDate.now().plusYears(1);
        topUpOn(aYearOut.toString()).topUp();

        CollectionEvent cancelled = events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30)).get(0);
        events.delete(cancelled.id());

        // A holiday cancellation. The next morning's run must not undo it.
        topUpOn(aYearOut.plusDays(1).toString()).topUp();

        assertThat(events.findUpcoming(Sector.north, aYearOut, aYearOut.plusDays(30)))
                .noneMatch(event -> event.collectionDate().equals(cancelled.collectionDate())
                        && event.collectionType() == cancelled.collectionType()
                        && event.sector() == cancelled.sector());
    }
}
