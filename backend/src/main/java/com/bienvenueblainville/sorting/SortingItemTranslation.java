package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;

public record SortingItemTranslation(
        Long id,
        Long itemId,
        LanguageCode languageCode,
        String name,
        String instruction,
        String location
) {
}
