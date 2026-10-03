package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;

import java.time.LocalDate;

/**
 * A day the city does not collect, and how far collection moves because of it.
 *
 * @param shiftDays how many days a collection landing on {@code holidayDate}
 *                  moves forward; at least 1, enforced by a CHECK constraint
 *                  and clamped again in {@link HolidayShift}
 * @param sector    which sectors observe it; {@code all} is the normal case
 */
public record CollectionHoliday(
        Long id,
        LocalDate holidayDate,
        String nameFr,
        String nameEn,
        String nameZh,
        int shiftDays,
        Sector sector,
        String sourceUrl,
        boolean active
) {
    /**
     * Whether this holiday moves a collection belonging to {@code ruleSector}.
     *
     * <p>A sector-scoped holiday deliberately does <em>not</em> move a
     * city-wide collection. Organics run on one day for the whole territory,
     * so "the north has a holiday" cannot move them without moving the south
     * along with it — a shift that would be wrong for half the city. An
     * exception that really applies everywhere is recorded as {@code all},
     * which is what a statutory holiday is.
     */
    public boolean applies(Sector ruleSector) {
        return sector == Sector.all || sector == ruleSector;
    }
}
