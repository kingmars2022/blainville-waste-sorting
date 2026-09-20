package com.bienvenueblainville.preference.dto;

import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.common.Sector;
import jakarta.validation.constraints.NotNull;


public record UserPreferenceRequest(
        @NotNull Sector sector,
        @NotNull LanguageCode languageCode,
        boolean reminderEnabled
) {
}
