package com.bienvenueblainville.assistant;

import com.bienvenueblainville.common.LanguageCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The retrieval half of the assistant: turns a resident's question into the
 * handful of sorting-guide entries that could actually answer it.
 *
 * <p>This is the part that decides whether the assistant can answer at all.
 * Everything downstream is forbidden from inventing facts, so if retrieval
 * comes back empty the assistant refuses rather than guessing — which makes
 * the cutoff below the most consequential number in the feature.
 */
@Component
public class SortingGuideRetriever {
    /** Never send more than this many entries to the model. */
    static final int MAX_RESULTS = 5;

    /**
     * Keep only entries scoring at least this fraction of the best match.
     *
     * <p>Relative, not absolute, on purpose: MySQL's natural-language relevance
     * score is not normalized — it depends on term frequency across the whole
     * table, so the same query scores differently as the guide grows. An
     * absolute floor tuned against today's 15 entries would silently rot. A
     * ratio against the top hit is scale-free.
     *
     * <p>Measured against the seeded data, a genuine match scores roughly
     * 2–15x the incidental n-gram overlap of unrelated rows, so 0.4 separates
     * them with room to spare in both directions.
     */
    static final double RELATIVE_CUTOFF = 0.4;

    private final SortingSearchMapper mapper;

    public SortingGuideRetriever(SortingSearchMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Chinese is searched through the ngram-parsed columns, French and English
     * through the word-parsed ones.
     *
     * <p>This is not a style preference. The ngram parser is what makes
     * Chinese searchable at all, and is simultaneously what makes a French or
     * English refusal impossible: incidental bigram overlap gave a question
     * the guide cannot answer a higher score than some real matches. See V7
     * for the measurements.
     */
    static boolean usesNgramParser(LanguageCode language) {
        return language == LanguageCode.zh;
    }

    public List<RetrievedItem> retrieve(String question, LanguageCode language) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        // Function words are stripped first, or a single stray "est" is enough
        // to make the assistant answer a question it should refuse.
        String normalized = QueryNormalizer.normalize(question, language);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<RetrievedItem> candidates =
                mapper.search(language, normalized, MAX_RESULTS, usesNgramParser(language));
        if (candidates.isEmpty()) {
            return List.of();
        }

        // The mapper already returns them best-first.
        double best = candidates.get(0).score();
        return candidates.stream()
                .filter(item -> item.score() >= best * RELATIVE_CUTOFF)
                .toList();
    }
}
