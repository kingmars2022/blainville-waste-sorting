package com.bienvenueblainville.agent.dto;

import java.util.List;

/**
 * @param completed how many steps ran before the outcome below
 * @param results   one line per step that ran
 * @param failure   null on success. Steps run in order and stop at the first
 *                  failure, so this plus {@code completed} says exactly how far
 *                  the change got — which an administrator needs in order to
 *                  know what to fix.
 */
public record AgentExecutionView(
        int completed,
        int total,
        List<String> results,
        String failure
) {
}
