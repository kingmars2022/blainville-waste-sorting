package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;

/**
 * One of the short "fruits, legumes, pain" hints under a sorting card.
 *
 * @param position kept explicit, because the order an administrator typed them
 *                 in is the order a resident should read them
 */
public record SortingItemExample(
        Long id,
        Long itemId,
        LanguageCode languageCode,
        int position,
        String example
) {
}
