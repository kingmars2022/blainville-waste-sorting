package com.bienvenueblainville.photo.dto;

import com.bienvenueblainville.assistant.dto.AssistantSource;

import java.util.List;

/**
 * @param identifiedAs what the vision model thought the object was, or null if
 *                     it could not tell. Shown to the resident even when the
 *                     answer fails, because "I saw a pizza box but the guide
 *                     has no entry" and "I could not see what that is" call for
 *                     completely different next steps
 * @param grounded     true only when the sorting guide covered the identified
 *                     material; the bin never comes from the model
 * @param sources      the guide entries the answer was allowed to use
 */
public record PhotoAnswer(
        String identifiedAs,
        boolean grounded,
        String answer,
        String provider,
        List<AssistantSource> sources
) {
}
