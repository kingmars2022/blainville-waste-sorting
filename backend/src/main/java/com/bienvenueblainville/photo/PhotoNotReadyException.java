package com.bienvenueblainville.photo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The photo is in storage; the Lambda has not produced its copy yet.
 *
 * <p>Its own type, and its own {@code Retry-After}, because this endpoint has
 * a second 503 that means something entirely different — no Anthropic key is
 * configured, which no amount of retrying will fix. A caller that retried both
 * would hammer a misconfiguration; a caller that retried neither would fail a
 * resident whose photo was a second from ready. The header is what tells them
 * apart, which is what it is for.
 */
public class PhotoNotReadyException extends ResponseStatusException {
    private static final String RETRY_AFTER_SECONDS = "2";

    public PhotoNotReadyException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "That photo is still being prepared. Try again in a moment.");
    }

    @Override
    public HttpHeaders getResponseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        return headers;
    }
}
