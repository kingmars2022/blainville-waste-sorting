package com.bienvenueblainville.agent;

import java.util.Map;

/**
 * One write the agent proposes, held until an administrator approves it.
 *
 * @param tool    which write it is
 * @param input   the arguments, exactly as the model produced them
 * @param summary a one-line description in plain language, for the confirmation
 *                screen — an administrator should not have to read JSON to
 *                decide whether to approve something
 */
public record PlannedStep(
        AgentToolName tool,
        Map<String, Object> input,
        String summary
) {
}
