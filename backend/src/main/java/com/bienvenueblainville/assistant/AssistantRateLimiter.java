package com.bienvenueblainville.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Per-IP hourly quota on the public assistant endpoint, counted in Redis.
 *
 * <p><b>Why this endpoint has a limiter and no other does.</b> Every other
 * public read is a database query the app can serve all day. This one can call
 * a paid API, from an unauthenticated endpoint, once per request. Without a
 * quota, a single loop costs real money and there is nothing in the
 * application to stop it.
 *
 * <p><b>Why it fails closed, when the cache fails open.</b> These are the same
 * Redis and the opposite policy, and the difference is deliberate: the cache
 * protects latency, so losing it should cost speed, never availability; this
 * limiter protects a budget, so losing it must not silently remove the only
 * spending cap. If the counter cannot be read, the assistant returns 503 and
 * the rest of the site — schedule, guide, notices, admin — is untouched.
 */
@Component
public class AssistantRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(AssistantRateLimiter.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMdd'T'HH");

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int requestsPerHour;

    // Two constructors, so the container needs telling which one is the bean's.
    // The package-private one below exists for tests to inject a fixed Clock.
    @Autowired
    public AssistantRateLimiter(StringRedisTemplate redis, AssistantProperties properties) {
        this(redis, Clock.systemDefaultZone(), properties.rateLimit().requestsPerHour());
    }

    AssistantRateLimiter(StringRedisTemplate redis, Clock clock, int requestsPerHour) {
        this.redis = redis;
        this.clock = clock;
        this.requestsPerHour = requestsPerHour;
    }

    /**
     * @throws ResponseStatusException 429 when the caller is over quota, 503 when
     *                                 the quota cannot be counted at all
     */
    public void check(String clientId) {
        // A fixed hourly window, not a sliding one: INCR + EXPIRE is atomic
        // enough for a cost cap, costs one round trip, and cannot leak memory.
        // Worst case a caller gets 2x the quota across a window boundary,
        // which does not matter for what this is protecting.
        String key = "assistant:ratelimit:" + LocalDateTime.now(clock).format(HOUR) + ":" + clientId;

        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofHours(2));
            }
        } catch (RuntimeException e) {
            log.error("Assistant rate limit cannot be counted; refusing the request rather than "
                    + "serving it uncapped", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The assistant is temporarily unavailable. The rest of the sorting guide still works.");
        }

        if (count != null && count > requestsPerHour) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many assistant questions from this address. Try again within the hour.");
        }
    }
}
