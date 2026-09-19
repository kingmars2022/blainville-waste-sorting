package com.bienvenueblainville.insights;

import com.bienvenueblainville.common.LanguageCode;

import java.time.Instant;
import java.util.List;

/**
 * One resident asking what to do with something.
 *
 * <p>Recorded so the city can find out what it has not written down. Today a
 * question the guide cannot answer produces a polite "check blainville.ca" and
 * then vanishes — nobody learns that fourteen people asked about aquariums
 * last month. That is the gap this stream closes.
 *
 * <p><b>No resident is identified here.</b> No IP address, no account id, no
 * photo. The question and what the guide did with it is the whole record,
 * because the only use for it is deciding which entries to write next, and
 * anything more would be collecting for its own sake.
 *
 * @param source       {@code text} when typed, {@code photo} when identified
 *                     from an image — the same material arrives worded very
 *                     differently through the two, which is exactly what makes
 *                     these documents worth keeping in their own shapes
 * @param askedAbout   what the resident typed, or what the vision model named
 * @param searchedFor  what retrieval actually searched with, after stopwords
 *                     and synonyms; the difference between this and
 *                     {@code askedAbout} is where retrieval bugs show up
 * @param grounded     false when the guide had nothing — the rows that matter
 * @param topScore     relevance of the best match, 0 when there was none
 * @param matchedNames the guide entries used, for spotting a near miss that
 *                     matched the wrong thing
 */
public record QueryEvent(
        String queryId,
        String source,
        LanguageCode language,
        String askedAbout,
        String searchedFor,
        boolean grounded,
        double topScore,
        List<String> matchedNames,
        Instant occurredAt
) {
    public static final String TOPIC = "blainville.resident.queries";
}
