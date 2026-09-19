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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantRateLimiterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AssistantRateLimiter limiter = new AssistantRateLimiter(redis,
            Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC), 30);
    private final List<String> keys = List.of("assistant:ratelimit:20260919T12:127.0.0.1");

    @Test
    void allowsTheLastRequestAndRejectsTheNext() {
        when(redis.execute(any(RedisScript.class), eq(keys))).thenReturn(30L, 31L);
        limiter.check("127.0.0.1");
        assertStatus(429);
    }

    @Test
    void refusesWhenRedisReturnsNoCounter() {
        assertStatus(503);
    }

    @Test
    void refusesWhenRedisFails() {
        when(redis.execute(any(RedisScript.class), eq(keys)))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        assertStatus(503);
    }

    private void assertStatus(int expected) {
        assertThatThrownBy(() -> limiter.check("127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(expected));
    }
}
