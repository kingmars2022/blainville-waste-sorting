package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;

public record SortingItemKeyword(Long id, Long itemId, LanguageCode languageCode, String keyword) {
}
