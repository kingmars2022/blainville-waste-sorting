package com.bienvenueblainville.preference;

import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.common.Sector;

import java.time.LocalTime;

public record UserPreference(
        Long userId,
        Sector sector,
        LanguageCode languageCode,
        boolean reminderEnabled,
        LocalTime reminderTime
) {
}
