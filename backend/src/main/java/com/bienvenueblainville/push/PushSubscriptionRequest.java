package com.bienvenueblainville.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A subscription as the browser hands it over.
 *
 * <p>The endpoint is restricted to https. A push endpoint is a URL this server
 * will POST to on a schedule it does not control, so an unvalidated one turns
 * the notice pipeline into a request forgery primitive pointed wherever the
 * caller likes.
 */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 500)
        @Pattern(regexp = "^https://.+", message = "must be an https push endpoint")
        String endpoint,

        @NotBlank @Size(max = 255) String p256dh,
        @NotBlank @Size(max = 64) String auth,

        @Pattern(regexp = "fr|en|zh", message = "must be fr, en or zh")
        String languageCode
) {
}
