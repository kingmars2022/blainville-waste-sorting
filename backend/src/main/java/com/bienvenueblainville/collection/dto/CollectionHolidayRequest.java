package com.bienvenueblainville.collection.dto;

import com.bienvenueblainville.common.Sector;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A closed day as an administrator supplies it.
 *
 * <p>All three names are required, for the same reason a sorting entry cannot
 * be saved without a French translation: the explanation appended to a moved
 * collection is shown in the resident's own language, and a missing one would
 * surface as a blank reason on the day it matters most.
 *
 * <p>{@code shiftDays} is bounded at both ends in three places - here, by a
 * CHECK constraint in {@code V14}, and by a clamp in {@code HolidayShift}.
 * Zero or less would turn the cascade loop into one that never ends, and a
 * constraint that lives in only one of those places is one refactor away from
 * living in none.
 */
public record CollectionHolidayRequest(
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 200) String nameFr,
        @NotBlank @Size(max = 200) String nameEn,
        @NotBlank @Size(max = 200) String nameZh,
        @Min(1) @Max(7) int shiftDays,
        @NotNull Sector sector,
        @Size(max = 1000) String sourceUrl,
        boolean active
) {
}
