package com.bienvenueblainville.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantRateLimiterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private static final Clock NOON_UTC = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);

    /** Configured with Claude, so an uncountable quota means uncapped spending. */
    private final AssistantRateLimiter limiter = new AssistantRateLimiter(redis, NOON_UTC, 30, true);
    private final List<String> keys = List.of("assistant:ratelimit:20260919T12:127.0.0.1");

    @Test
    void allowsTheLastRequestAndRejectsTheNext() {
        when(redis.execute(any(RedisScript.class), eq(keys))).thenReturn(30L, 31L);
        limiter.check("127.0.0.1");
        assertStatus(429);
    }

    @Test
    void refusesWhenRedisReturnsNoCounter() {
        when(redis.execute(any(RedisScript.class), eq(keys))).thenReturn(null);
        assertStatus(503);
    }

    @Test
    void refusesWhenRedisFails() {
        when(redis.execute(any(RedisScript.class), eq(keys)))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        assertStatus(503);
    }

    @Test
    void servesAnywayWhenRedisFailsAndNothingIsBeingSpent() {
        // The default template composer costs nothing per request, so an
        // unreachable Redis must not take down a free feature - the same
        // reasoning that makes the read cache fail open. The quota is still
        // enforced whenever Redis answers.
        AssistantRateLimiter free = new AssistantRateLimiter(redis, NOON_UTC, 30, false);
        when(redis.execute(any(RedisScript.class), eq(keys)))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        assertThatNoException().isThrownBy(() -> free.check("127.0.0.1"));
    }

    @Test
    void stillEnforcesTheQuotaInFreeModeWhenRedisAnswers() {
        AssistantRateLimiter free = new AssistantRateLimiter(redis, NOON_UTC, 30, false);
        when(redis.execute(any(RedisScript.class), eq(keys))).thenReturn(31L);

        assertThatThrownBy(() -> free.check("127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(429));
    }

    private void assertStatus(int expected) {
        assertThatThrownBy(() -> limiter.check("127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(expected));
    }
}
