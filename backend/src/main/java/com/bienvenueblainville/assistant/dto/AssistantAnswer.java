package com.bienvenueblainville.assistant.dto;

import java.util.List;

/**
 * @param answer   the text to show the resident
 * @param grounded false when the guide had nothing relevant; the answer is then
 *                 an explicit "I don't know", never a guess
 * @param provider what produced the wording ("template" or "anthropic:<model>"),
 *                 so the caller is never guessing whether a model was involved
 * @param sources  the guide entries the answer was allowed to use, so a
 *                 resident can check it and a developer can debug retrieval
 */
public record AssistantAnswer(
        String answer,
        boolean grounded,
        String provider,
        List<AssistantSource> sources
) {
}
