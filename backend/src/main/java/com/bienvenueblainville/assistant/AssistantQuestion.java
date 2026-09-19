package com.bienvenueblainville.assistant;

import com.bienvenueblainville.common.LanguageCode;

/**
 * A question and the language it should be answered in.
 *
 * <p>Public because {@link AnswerComposer} is: an interface nobody outside this
 * package could implement or call was not the intent, and the photo path now
 * composes answers through the same seam.
 */
public record AssistantQuestion(String text, LanguageCode language) {
}
