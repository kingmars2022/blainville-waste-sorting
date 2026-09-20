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
            redis.opsForValue().set(key(plan.id(), plan.adminUserId()),
                    objectMapper.writeValueAsString(plan), TTL);
            return plan.id();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the plan", e);
        }
    }

    /**
     * Takes the plan and removes it, in one operation that cannot interleave.
     *
     * <p>This used to be a {@code GET} followed by a {@code DELETE}, which made
     * "single-use" a comment rather than a guarantee: two approvals arriving at
     * once both read the plan before either deleted it, and both executed it.
     * Every write in the plan happened twice. {@code GETDEL} closes that
     * window — exactly one caller can be handed the value.
     *
     * <p>Deleted before execution, not after: a retry following a partial
     * failure must be a fresh plan built against the state that failure left
     * behind, not a replay of stale steps.
     *
     * @param adminUserId the administrator asking to execute. A plan is bound to
     *                    whoever it was shown to — approval means "I read this
     *                    and I accept it", which no one can do on another's
     *                    behalf — and that binding is the key itself rather than
     *                    a check after the read. Another administrator computes
     *                    a different key, so they find nothing, delete nothing,
     *                    and cannot probe for plan ids.
     */
    public AgentPlan consume(String planId, Long adminUserId) {
        String json = redis.opsForValue().getAndDelete(key(planId, adminUserId));

        return Optional.ofNullable(json).map(this::read).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "That plan has expired or was already run. Ask again to get a fresh one."));
    }

    /**
     * Owner first, then plan id. Binding the two into the key is what lets the
     * ownership check and the single-use guarantee be the same atomic step.
     */
    private static String key(String planId, Long adminUserId) {
        return PREFIX + adminUserId + ":" + planId;
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
