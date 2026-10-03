package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moves generated collection dates off the days the city does not collect.
 *
 * <p>A pure function over a fixed set of holidays, deliberately separate from
 * {@link CollectionCalendarTopUp}: the question worth testing here — what
 * happens to a collection on the 25th of December when the 26th is also closed
 * — should be answerable without a database, a clock or a Spring context.
 */
public final class HolidayShift {
    /**
     * A collection that landed on a holiday moves, then may land on another.
     * Each step moves strictly forward and the holiday set is finite, so the
     * loop terminates on any sane data; this bounds it on insane data instead
     * of hanging the daily job. Seven consecutive closed days is already far
     * beyond anything a municipal calendar expresses.
     */
    private static final int MAX_STEPS = 7;

    private final Map<LocalDate, List<CollectionHoliday>> byDate;

    private HolidayShift(Map<LocalDate, List<CollectionHoliday>> byDate) {
        this.byDate = byDate;
    }

    public static HolidayShift of(List<CollectionHoliday> holidays) {
        return new HolidayShift(holidays.stream()
                .filter(CollectionHoliday::active)
                .collect(Collectors.groupingBy(CollectionHoliday::holidayDate)));
    }

    public static HolidayShift none() {
        return new HolidayShift(Map.of());
    }

    /**
     * Where a collection originally falling on {@code date} actually happens.
     *
     * @return the date it moved to and the holidays that moved it, in order;
     *         an empty cause list means the date was never a holiday
     */
    public Shifted apply(LocalDate date, Sector ruleSector) {
        LocalDate moved = date;
        List<CollectionHoliday> causes = new ArrayList<>();

        for (int step = 0; step < MAX_STEPS; step++) {
            CollectionHoliday hit = byDate.getOrDefault(moved, List.of()).stream()
                    .filter(holiday -> holiday.applies(ruleSector))
                    // Several rows can share a date when one is sector-scoped
                    // and another is city-wide. The longest shift wins: a
                    // collection that has to clear two closures clears both.
                    .max((a, b) -> Integer.compare(a.shiftDays(), b.shiftDays()))
                    .orElse(null);
            if (hit == null) {
                return new Shifted(moved, List.copyOf(causes));
            }
            causes.add(hit);
            moved = moved.plusDays(Math.max(1, hit.shiftDays()));
        }
        return new Shifted(moved, List.copyOf(causes));
    }

    /**
     * @param date   where the collection actually happens
     * @param causes the holidays it was moved past, empty when it did not move
     */
    public record Shifted(LocalDate date, List<CollectionHoliday> causes) {
        public boolean moved() {
            return !causes.isEmpty();
        }

        /**
         * The rule's note with an explanation appended, so a resident reading
         * "Thursday" on an unexpected day is told why rather than left to
         * assume the site is broken. Returns the note unchanged when nothing
         * moved, including when the note itself is absent.
         */
        public String explain(String note, java.util.function.Function<CollectionHoliday, String> name, String template) {
            if (!moved()) {
                return note;
            }
            String reason = causes.stream().map(name).collect(Collectors.joining(", "));
            String sentence = template.replace("{holiday}", reason);
            return note == null || note.isBlank() ? sentence : note + " " + sentence;
        }
    }
}
