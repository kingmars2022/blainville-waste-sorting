package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionCalendarTopUpTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @Mock
    private CollectionScheduleRuleMapper rules;
    @Mock
    private CollectionEventMapper events;
    @Mock
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private CollectionCalendarTopUp topUpOn(String today, int horizonDays) {
        Clock clock = Clock.fixed(
                ZonedDateTime.of(LocalDate.parse(today).atStartOfDay(), ZONE).toInstant(), ZONE);
        return new CollectionCalendarTopUp(rules, events, cacheManager, clock, true, horizonDays);
    }

    private CollectionScheduleRule weeklyThursday(String anchor, String generatedThrough) {
        return new CollectionScheduleRule(1L, Sector.all, CollectionType.organic, BinColor.brown,
                LocalDate.parse(anchor), 1, LocalDate.parse(generatedThrough),
                "fr", "en", "zh", "https://blainville.ca", true);
    }

    @Test
    void generatesTheOccurrencesThatTheHandWrittenSeedStoppedShortOf() {
        // The exact situation the seed left behind: rows end on 2026-10-29,
        // and it is now the day after.
        when(rules.findActive()).thenReturn(List.of(weeklyThursday("2026-09-10", "2026-10-29")));

        int written = topUpOn("2026-10-30", 28).topUp();

        ArgumentCaptor<List<CollectionEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).insertGenerated(captor.capture());

        assertThat(captor.getValue())
                .extracting(CollectionEvent::collectionDate)
                .containsExactly(
                        LocalDate.parse("2026-11-05"),
                        LocalDate.parse("2026-11-12"),
                        LocalDate.parse("2026-11-19"),
                        LocalDate.parse("2026-11-26"));
        assertThat(written).isEqualTo(4);
        // The mark moves to the horizon, not to the last date written: the
        // whole window has been considered, so nothing in it is reconsidered.
        verify(rules).advanceGeneratedThrough(1L, LocalDate.parse("2026-11-27"));
    }

    @Test
    void neverRegeneratesAnOccurrenceInsideTheMarkedWindow() {
        // The mark is ahead of today, which is what it looks like after a
        // previous run - and, crucially, after an administrator deleted a
        // holiday occurrence from inside that window.
        when(rules.findActive()).thenReturn(List.of(weeklyThursday("2026-09-10", "2026-12-31")));

        int written = topUpOn("2026-11-02", 28).topUp();

        assertThat(written).isZero();
        verify(events, never()).insertGenerated(any());
        verify(rules, never()).advanceGeneratedThrough(anyLong(), any());
    }

    @Test
    void resumesFromTodayAfterAGapRatherThanBackfillingTheMonthsItMissed() {
        // Down since November; it is now March. Nobody needs the collections
        // that already happened, but tonight's has to be there.
        when(rules.findActive()).thenReturn(List.of(weeklyThursday("2026-09-10", "2026-11-27")));

        topUpOn("2027-03-04", 14).topUp();

        ArgumentCaptor<List<CollectionEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).insertGenerated(captor.capture());

        // 2027-03-04 is itself a Thursday: included, and nothing before it.
        // The horizon is inclusive, so the fortnight ends on a third occurrence.
        assertThat(captor.getValue())
                .extracting(CollectionEvent::collectionDate)
                .containsExactly(
                        LocalDate.parse("2027-03-04"),
                        LocalDate.parse("2027-03-11"),
                        LocalDate.parse("2027-03-18"));
    }

    @Test
    void keepsBiweeklyRulesOnTheirOwnPhase() {
        CollectionScheduleRule south = new CollectionScheduleRule(2L, Sector.south,
                CollectionType.recycling, BinColor.blue,
                LocalDate.parse("2026-09-15"), 2, LocalDate.parse("2026-10-27"),
                null, null, null, null, true);
        when(rules.findActive()).thenReturn(List.of(south));

        topUpOn("2026-10-30", 42).topUp();

        ArgumentCaptor<List<CollectionEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).insertGenerated(captor.capture());

        // Every second Tuesday, counted from the anchor - not every Tuesday.
        assertThat(captor.getValue())
                .extracting(CollectionEvent::collectionDate)
                .containsExactly(
                        LocalDate.parse("2026-11-10"),
                        LocalDate.parse("2026-11-24"),
                        LocalDate.parse("2026-12-08"));
    }

    @Test
    void carriesTheRulesNotesOntoEveryGeneratedOccurrence() {
        when(rules.findActive()).thenReturn(List.of(weeklyThursday("2026-09-10", "2026-10-29")));

        topUpOn("2026-10-30", 7).topUp();

        ArgumentCaptor<List<CollectionEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).insertGenerated(captor.capture());

        assertThat(captor.getValue()).singleElement().satisfies(event -> {
            assertThat(event.id()).isNull();
            assertThat(event.sector()).isEqualTo(Sector.all);
            assertThat(event.collectionType()).isEqualTo(CollectionType.organic);
            assertThat(event.binColor()).isEqualTo(BinColor.brown);
            assertThat(event.noteFr()).isEqualTo("fr");
            assertThat(event.noteZh()).isEqualTo("zh");
            assertThat(event.sourceUrl()).isEqualTo("https://blainville.ca");
        });
    }

    @Test
    void doesNothingAtAllWhenTurnedOff() {
        Clock clock = Clock.fixed(
                ZonedDateTime.of(LocalDate.parse("2026-10-30").atStartOfDay(), ZONE).toInstant(), ZONE);
        new CollectionCalendarTopUp(rules, events, cacheManager, clock, false, 28).scheduledTopUp();

        verify(events, never()).insertGenerated(any());
    }
}
