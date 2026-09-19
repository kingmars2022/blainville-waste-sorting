package com.bienvenueblainville.agent;

import java.util.Map;

/**
 * Describes a proposed write in one line, <b>derived from the arguments that
 * will actually run</b>.
 *
 * <p>The obvious alternative — asking the model to describe its own steps — is
 * unsafe in a way that is easy to miss: the description and the arguments
 * would be two separate model outputs, and nothing forces them to agree. An
 * administrator approving "move Thursday's organics to Friday" could be
 * approving arguments that say something else. Generating the line from the
 * arguments means the confirmation screen cannot misrepresent what will
 * happen.
 */
final class StepSummariser {
    private StepSummariser() {
    }

    static String describe(AgentToolName tool, Map<String, Object> input) {
        return switch (tool) {
            case create_collection -> "Add a %s collection for sector %s on %s (%s bin)".formatted(
                    text(input, "collectionType"), text(input, "sector"),
                    text(input, "collectionDate"), text(input, "binColor"));
            case update_collection -> "Change collection event %s to a %s collection for sector %s on %s (%s bin)"
                    .formatted(text(input, "id"), text(input, "collectionType"), text(input, "sector"),
                            text(input, "collectionDate"), text(input, "binColor"));
            case delete_collection -> "Delete collection event %s".formatted(text(input, "id"));
            case create_notice -> "Publish notice \"%s\" from %s to %s (%s)".formatted(
                    text(input, "titleFr"), text(input, "startsOn"), text(input, "endsOn"),
                    Boolean.TRUE.equals(input.get("active")) ? "visible" : "hidden");
            case update_notice -> "Change notice %s to \"%s\", %s to %s (%s)".formatted(
                    text(input, "id"), text(input, "titleFr"), text(input, "startsOn"),
                    text(input, "endsOn"), Boolean.TRUE.equals(input.get("active")) ? "visible" : "hidden");
            case delete_notice -> "Delete notice %s permanently".formatted(text(input, "id"));
            default -> throw new IllegalArgumentException(tool + " is not a write tool");
        };
    }

    private static String text(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? "?" : String.valueOf(value);
    }
}
