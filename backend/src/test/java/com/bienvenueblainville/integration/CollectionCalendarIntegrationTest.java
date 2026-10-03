package com.bienvenueblainville.integration;

import com.bienvenueblainville.collection.CollectionCalendarTopUp;
import com.bienvenueblainville.collection.CollectionEvent;
import com.bienvenueblainville.collection.CollectionEventMapper;
import com.bienvenueblainville.collection.CollectionHolidayMapper;
import com.bienvenueblainville.collection.CollectionScheduleRule;
import com.bienvenueblainville.collection.CollectionType;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
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
    private CollectionHolidayMapper holidays;
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
        jdbc.update("delete from collection_holiday where source_url = 'test://holiday-shift'");
        marksBefore.forEach((id, mark) ->
                jdbc.update("update collection_schedule_rule set generated_through = ? where id = ?", mark, id));
    }

    private CollectionCalendarTopUp topUpOn(String today) {
        Clock clock = Clock.fixed(
                ZonedDateTime.of(LocalDate.parse(today).atStartOfDay(), ZONE).toInstant(), ZONE);
        return new CollectionCalendarTopUp(rules, holidays, events, cacheManager, clock, true, 180);
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

    /**
     * Puts the calendar in a known state far enough out that no other test in
     * the suite has generated there, closes the next organics collection, and
     * returns the day that collection was originally due.
     *
     * <p>Self-contained on purpose: these assertions are about a specific day
     * being empty, which any earlier top-up in the same database would
     * otherwise have filled. The marks are restored by {@link #restore()}.
     */
    private LocalDate closeTheNextOrganicsCollection(LocalDate today) {
        jdbc.update("update collection_schedule_rule set generated_through = ?", today.minusDays(1));
        jdbc.update("delete from collection_event where collection_date >= ?", today);

        CollectionScheduleRule organics = rules.findActive().stream()
                .filter(rule -> rule.collectionType() == CollectionType.organic)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed no longer has an organics rule"));
        LocalDate closed = organics.occurrencesAfter(today, today.plusDays(30)).getFirst();

        jdbc.update("insert into collection_holiday "
                        + "(holiday_date, name_fr, name_en, name_zh, shift_days, sector, source_url) "
                        + "values (?, ?, ?, ?, 1, 'all', 'test://holiday-shift')",
                closed, "Jour test", "Test holiday", "测试假日");
        return closed;
    }

    @Test
    void aCollectionLandingOnAHolidayIsWrittenOnTheDayItActuallyHappens() {
        LocalDate today = LocalDate.parse("2029-03-01");
        LocalDate closed = closeTheNextOrganicsCollection(today);

        topUpOn(today.toString()).topUp();

        List<CollectionEvent> window = events.findUpcoming(Sector.all, closed, closed.plusDays(1));
        assertThat(window)
                .as("nothing is collected on the closed day")
                .noneMatch(event -> event.collectionDate().equals(closed));
        assertThat(window)
                .as("and the collection it would have been appears the day after")
                .anyMatch(event -> event.collectionDate().equals(closed.plusDays(1)));
    }

    @Test
    void aMovedCollectionSaysWhyItMoved() {
        LocalDate today = LocalDate.parse("2029-06-01");
        LocalDate closed = closeTheNextOrganicsCollection(today);

        topUpOn(today.toString()).topUp();

        // A resident who sees an unfamiliar day is owed the reason, in their
        // own language, rather than being left to assume the site is wrong.
        assertThat(events.findUpcoming(Sector.all, closed.plusDays(1), closed.plusDays(1)))
                .anySatisfy(event -> {
                    assertThat(event.noteFr()).contains("Jour test");
                    assertThat(event.noteEn()).contains("Test holiday");
                    assertThat(event.noteZh()).contains("测试假日");
                });
    }

    /**
     * The generated calendar, checked line by line against the document it is
     * supposed to reproduce.
     *
     * <p>The dates below are read off the Ville de Blainville "Calendrier des
     * collectes residentielles 2026" - black bin, blue triangle, brown apple -
     * for November 2026, a month chosen because it contains two full
     * fortnights of both alternating collections. Before V15 this test failed
     * on every single row: garbage was missing from the database entirely, and
     * the recycling rules were anchored to the garbage fortnight.
     *
     * <p>It is the test the project did not have. Everything else about the
     * calendar - that it does not run out, that it does not duplicate, that a
     * cancellation stays cancelled - was true of a calendar printing the wrong
     * bin.
     */
    @Test
    void theGeneratedCalendarMatchesTheCityPrintedCalendar() {
        LocalDate today = LocalDate.parse("2026-10-20");
        jdbc.update("update collection_schedule_rule set generated_through = ?", today.minusDays(1));
        jdbc.update("delete from collection_event where collection_date >= ?", today);

        topUpOn(today.toString()).topUp();

        LocalDate from = LocalDate.parse("2026-11-01");
        LocalDate to = LocalDate.parse("2026-11-30");

        // Tuesdays south of boulevard de la Seigneurie, plus the city-wide
        // Thursday organics that findUpcoming folds in.
        assertThat(events.findUpcoming(Sector.south, from, to))
                .extracting(CollectionEvent::collectionDate, CollectionEvent::collectionType)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(LocalDate.parse("2026-11-03"), CollectionType.recycling),
                        Tuple.tuple(LocalDate.parse("2026-11-10"), CollectionType.garbage),
                        Tuple.tuple(LocalDate.parse("2026-11-17"), CollectionType.recycling),
                        Tuple.tuple(LocalDate.parse("2026-11-24"), CollectionType.garbage),
                        Tuple.tuple(LocalDate.parse("2026-11-05"), CollectionType.organic),
                        Tuple.tuple(LocalDate.parse("2026-11-12"), CollectionType.organic),
                        Tuple.tuple(LocalDate.parse("2026-11-19"), CollectionType.organic),
                        Tuple.tuple(LocalDate.parse("2026-11-26"), CollectionType.organic));

        // Wednesdays north of it, one day behind the south throughout.
        assertThat(events.findUpcoming(Sector.north, from, to))
                .extracting(CollectionEvent::collectionDate, CollectionEvent::collectionType)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(LocalDate.parse("2026-11-04"), CollectionType.recycling),
                        Tuple.tuple(LocalDate.parse("2026-11-11"), CollectionType.garbage),
                        Tuple.tuple(LocalDate.parse("2026-11-18"), CollectionType.recycling),
                        Tuple.tuple(LocalDate.parse("2026-11-25"), CollectionType.garbage),
                        Tuple.tuple(LocalDate.parse("2026-11-05"), CollectionType.organic),
                        Tuple.tuple(LocalDate.parse("2026-11-12"), CollectionType.organic),
                        Tuple.tuple(LocalDate.parse("2026-11-19"), CollectionType.organic),
                        Tuple.tuple(LocalDate.parse("2026-11-26"), CollectionType.organic));
    }

    /**
     * The one shift the 2026 calendar prints: an asterisk on 1 January reading
     * "Collecte du 1er reportee au 2". New Year's Day 2026 is a Thursday, so
     * it is the organics collection that moves.
     */
    @Test
    void theOnlyHolidayTheCityPrintsIsTheOneInTheDatabase() {
        assertThat(jdbc.queryForList(
                "select holiday_date, shift_days, source_url from collection_holiday where active = true"))
                .as("V14's guesses were removed by V15; only the cited one survives")
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("holiday_date").toString()).isEqualTo("2026-01-01");
                    assertThat(((Number) row.get("shift_days")).intValue()).isEqualTo(1);
                    assertThat(row.get("source_url")).asString().contains("blainville.ca");
                });
    }
}
