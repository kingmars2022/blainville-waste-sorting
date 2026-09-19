package com.bienvenueblainville.sorting.dto;

import java.util.List;

/**
 * @param examples     the short hints under a card, in the order an
 *                     administrator entered them
 * @param availability seasonal or on-request wording, null for year-round
 *                     services
 */
public record TranslationView(
        String name,
        String instruction,
        String location,
        String availability,
        List<String> examples
) {
}
