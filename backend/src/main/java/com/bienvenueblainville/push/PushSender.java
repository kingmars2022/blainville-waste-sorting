package com.bienvenueblainville.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Posts one encrypted message to one push service.
 *
 * <p>Separated from the fan-out so the thing that talks to the network is the
 * thing that can be pointed at a test server.
 */
public class PushSender {
    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    /** RFC 8030 section 5.2: how long the service may hold an undelivered message. */
    private static final int TTL_SECONDS = (int) Duration.ofHours(12).toSeconds();

    /** RFC 8188's record size. The browser allocates against it. */
    private static final int RECORD_SIZE = 4096;

    private final HttpClient http;
    private final VapidAuthentication vapid;

    public PushSender(HttpClient http, VapidAuthentication vapid) {
        this.http = http;
        this.vapid = vapid;
    }

    /**
     * @return what the push service said, mapped to what the caller should do
     */
    public Result send(PushSubscription subscription, String payload) {
        byte[] body;
        try {
            body = WebPushCrypto.encrypt(
                    WebPushCrypto.fromBase64Url(subscription.p256dh()),
                    WebPushCrypto.fromBase64Url(subscription.auth()),
                    payload.getBytes(StandardCharsets.UTF_8),
                    // Fresh per message. Reusing either repeats the AES-GCM
                    // nonce across messages to the same device.
                    WebPushCrypto.generateKeyPair(),
                    WebPushCrypto.randomBytes(16),
                    RECORD_SIZE);
        } catch (IllegalArgumentException e) {
            // Stored keys that will not decode will never decode. Treat the
            // subscription as gone rather than retrying it forever.
            log.warn("Subscription {} has keys that cannot be used; dropping it", subscription.id(), e);
            return Result.GONE;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.endpoint()))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", vapid.headerFor(subscription.endpoint()))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(TTL_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return classify(response.statusCode(), subscription);
        } catch (IOException e) {
            log.warn("Could not reach the push service for subscription {}", subscription.id(), e);
            return Result.RETRYABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.RETRYABLE;
        }
    }

    private Result classify(int status, PushSubscription subscription) {
        if (status >= 200 && status < 300) {
            return Result.SENT;
        }
        if (status == 404 || status == 410) {
            // The subscription is over - the browser was uninstalled, the
            // permission revoked, the service expired it. Keeping the row means
            // paying for a guaranteed failure on every future notice.
            return Result.GONE;
        }
        if (status == 429 || status >= 500) {
            log.warn("Push service answered {} for subscription {}", status, subscription.id());
            return Result.RETRYABLE;
        }
        // 400 or 401 is this application's fault - a malformed body or a VAPID
        // header the service rejected - and will not fix itself by retrying.
        log.error("Push service rejected subscription {} with {}", subscription.id(), status);
        return Result.REJECTED;
    }

    public enum Result {
        SENT,
        /** The subscription no longer exists; delete it. */
        GONE,
        /** Temporary. Nothing here retries; the inbox already has the notice. */
        RETRYABLE,
        /** Our fault. Logged loudly rather than swallowed. */
        REJECTED
    }
}
