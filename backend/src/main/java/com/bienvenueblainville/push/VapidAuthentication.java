package com.bienvenueblainville.push;

import io.jsonwebtoken.Jwts;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * The {@code Authorization} header a push service requires: RFC 8292, VAPID.
 *
 * <p>It is what identifies the sender. Without it a push service will not
 * accept the message at all, and with it the service can rate-limit or contact
 * whoever is sending - which is why {@code subject} is a real mailto: or https:
 * URL rather than a placeholder.
 *
 * <p>The audience is the push endpoint's <em>origin</em>, not the endpoint.
 * Signing the full URL is the mistake that produces a 401 from every push
 * service while looking entirely reasonable in a log.
 */
public final class VapidAuthentication {
    /** RFC 8292 section 2: at most 24 hours. Kept well under it. */
    private static final Duration LIFETIME = Duration.ofHours(12);

    private final VapidKeys keys;
    private final String subject;
    private final Clock clock;

    public VapidAuthentication(VapidKeys keys, String subject, Clock clock) {
        this.keys = keys;
        this.subject = subject;
        this.clock = clock;
    }

    public String headerFor(String endpoint) {
        Instant now = Instant.now(clock);
        // A plain string claim rather than .audience().add(), which serialises a
        // single audience as a one-element array. Both are legal JWT; the
        // reference implementation emits a string, and a push service is not
        // the place to find out which ones are strict. Cross-checked against
        // web-push's output.
        String token = Jwts.builder()
                .claim("aud", origin(endpoint))
                .expiration(Date.from(now.plus(LIFETIME)))
                .subject(subject)
                .signWith(keys.keyPair().getPrivate(), Jwts.SIG.ES256)
                .compact();

        return "vapid t=" + token + ", k=" + keys.publicKeyBase64Url();
    }

    static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Push endpoint is not an absolute URL: " + endpoint);
        }
        StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            origin.append(':').append(uri.getPort());
        }
        return origin.toString();
    }
}
