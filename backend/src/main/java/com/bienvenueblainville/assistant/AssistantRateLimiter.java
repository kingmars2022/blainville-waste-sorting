package com.bienvenueblainville.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
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
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then
                redis.call('EXPIRE', KEYS[1], 7200)
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int requestsPerHour;

    // Two constructors, so the container needs telling which one is the bean's.
    // The package-private one below exists for tests to inject a fixed Clock.
    @Autowired
    public AssistantRateLimiter(StringRedisTemplate redis, AssistantProperties properties) {
        this(redis, Clock.systemUTC(), properties.rateLimit().requestsPerHour());
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
        // One script installs the counter and expiry atomically. UTC avoids
        // local daylight-saving changes repeating or skipping an hourly key.
        String key = "assistant:ratelimit:" + LocalDateTime.now(clock).format(HOUR) + ":" + clientId;

        Long count;
        try {
            count = redis.execute(INCREMENT, List.of(key));
            if (count == null || count < 1L) {
                throw new IllegalStateException("Redis returned no valid quota counter");
            }
        } catch (RuntimeException e) {
            log.error("Assistant rate limit cannot be counted; refusing the request rather than "
                    + "serving it uncapped", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The assistant is temporarily unavailable. The rest of the sorting guide still works.");
        }

        if (count > requestsPerHour) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many assistant questions from this address. Try again within the hour.");
        }
    }
}
