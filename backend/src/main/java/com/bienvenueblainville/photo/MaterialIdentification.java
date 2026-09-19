package com.bienvenueblainville.photo;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * What the vision model is allowed to say about a photo.
 *
 * <p>Note what is <b>not</b> in this record: no bin colour, no destination, no
 * instruction. The model's entire job is to name the object; which bin it goes
 * in is decided afterwards by searching the municipal guide. That split is the
 * same rule the text assistant follows, and it is the reason this feature is
 * safe to ship — a vision model confidently saying "blue bin" from memory
 * would be wrong in exactly the way a resident cannot check.
 */
public record MaterialIdentification(
        @JsonPropertyDescription("The object, in two or three words, in the language requested. "
                + "Example: 'boîte à pizza', 'used battery', '塑料水瓶'.")
        String material,

        @JsonPropertyDescription("Up to three other words a resident might use for the same thing, "
                + "in the same language. Used to widen the search of the sorting guide.")
        List<String> alternateTerms,

        @JsonPropertyDescription("True only if the photo is too blurry, too dark, or too ambiguous "
                + "to name the object with confidence.")
        boolean uncertain
) {
    /** The search string handed to retrieval: the term plus its synonyms. */
    public String searchQuery() {
        if (alternateTerms == null || alternateTerms.isEmpty()) {
            return material;
        }
        return material + " " + String.join(" ", alternateTerms);
    }
}
