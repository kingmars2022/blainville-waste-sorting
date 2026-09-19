package com.bienvenueblainville.insights;

import java.util.List;

/**
 * Something residents keep asking about that the sorting guide does not cover.
 *
 * @param term      the words they used, lowercased
 * @param asks      how many times, in the window
 * @param languages which languages it was asked in - a term asked in all three
 *                  is a bigger gap than one asked in one
 * @param examples  a few of the original questions, so an administrator writing
 *                  the new entry can see how residents actually phrase it
 */
public record GuideGap(
        String term,
        long asks,
        List<String> languages,
        List<String> examples
) {
}
