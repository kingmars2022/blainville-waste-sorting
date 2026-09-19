package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;

public record SortingItemTranslation(
        Long id,
        Long itemId,
        LanguageCode languageCode,
        String name,
        String instruction,
        String location,
        /** Seasonal or on-request wording, e.g. "free collection in May, June and October". */
        String availability
) {
}
