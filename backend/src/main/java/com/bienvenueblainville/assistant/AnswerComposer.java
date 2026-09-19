package com.bienvenueblainville.assistant;

import java.util.List;

/**
 * Turns a question plus the guide entries retrieved for it into an answer.
 *
 * <p>This is the seam between "what the guide says" and "how it is worded".
 * Retrieval, grounding and the refusal rule live outside it, in
 * {@link AssistantService}, so that swapping the composer cannot change
 * <em>which</em> facts the assistant is allowed to use — only their phrasing.
 * A composer never sees the database and is never asked to answer without
 * context.
 */
public interface AnswerComposer {
    /** Reported on the API response so a caller always knows what answered. */
    String providerName();

    /**
     * @param context guaranteed non-empty; the refusal path never reaches here
     */
    String compose(AssistantQuestion question, List<RetrievedItem> context);

    default Composition composeWithMetadata(AssistantQuestion question, List<RetrievedItem> context) {
        return new Composition(compose(question, context), providerName());
    }

    record Composition(String text, String provider) {
    }
}
