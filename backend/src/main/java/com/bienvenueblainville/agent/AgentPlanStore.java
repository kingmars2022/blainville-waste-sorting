package com.bienvenueblainville.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds proposed plans between "show me what you would do" and "do it".
 *
 * <p>Redis rather than a field on the session, for the reason a plan expires
 * at all: an approval is only meaningful against the data the plan was built
 * from. Fifteen minutes later the schedule may have moved on, so a stale plan
 * should be gone rather than executable. A TTL expresses that directly, and
 * survives a redeploy between the two requests.
 */
@Component
public class AgentPlanStore {
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String PREFIX = "agent:plan:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AgentPlanStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String save(AgentPlan plan) {
        try {
            redis.opsForValue().set(PREFIX + plan.id(), objectMapper.writeValueAsString(plan), TTL);
            return plan.id();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the plan", e);
        }
    }

    /**
     * @param adminUserId the administrator asking to execute. A plan is bound to
     *                    whoever it was shown to: approval means "I read this
     *                    and I accept it", which no one else can do on their
     *                    behalf. A mismatch reads as not-found rather than
     *                    forbidden, so plan ids are not probeable.
     */
    public AgentPlan consume(String planId, Long adminUserId) {
        String key = PREFIX + planId;
        String json = redis.opsForValue().get(key);

        AgentPlan plan = Optional.ofNullable(json).map(this::read).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "That plan has expired or was already run. Ask again to get a fresh one."));

        if (!plan.adminUserId().equals(adminUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "That plan has expired or was already run. Ask again to get a fresh one.");
        }

        // Deleted before execution, not after: a plan is single-use, and a
        // retry after a partial failure must be a fresh plan built against the
        // state the failure left behind, not a replay of stale steps.
        redis.delete(key);
        return plan;
    }

    private AgentPlan read(String json) {
        try {
            return objectMapper.readValue(json, AgentPlan.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored plan could not be read back", e);
        }
    }

    public static String newPlanId() {
        return UUID.randomUUID().toString();
    }
}
