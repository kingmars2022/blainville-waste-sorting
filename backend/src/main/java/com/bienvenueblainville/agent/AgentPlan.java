package com.bienvenueblainville.agent;

import java.time.Instant;
import java.util.List;

/**
 * A proposal, not an action.
 *
 * @param id          opaque handle the administrator posts back to approve
 * @param adminUserId who asked; execution checks this, so one administrator
 *                    cannot execute a plan another one was shown
 * @param instruction what was originally typed, kept so the confirmation screen
 *                    can show request and proposal side by side
 * @param narrative   the model's own explanation of what it intends and why
 * @param steps       the writes, in the order they will run
 */
public record AgentPlan(
        String id,
        Long adminUserId,
        String instruction,
        String narrative,
        List<PlannedStep> steps,
        Instant createdAt
) {
}
