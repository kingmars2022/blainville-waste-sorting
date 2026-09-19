package com.bienvenueblainville.agent.dto;

import java.util.List;

/**
 * @param planId    null when the agent proposes nothing; there is then nothing
 *                  to approve and the UI must not offer a confirm button
 * @param narrative the agent's explanation, for the administrator to read
 * @param steps     what it wants to do, in order, described from the real
 *                  arguments
 */
public record AgentPlanView(
        String planId,
        String instruction,
        String narrative,
        List<AgentStepView> steps
) {
    public record AgentStepView(String tool, String summary) {
    }
}
