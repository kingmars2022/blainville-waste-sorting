import { api } from "./client";

export interface AgentStep {
  tool: string;
  /** Plain-language description, generated from the arguments that will actually run. */
  summary: string;
}

export interface AgentPlan {
  /** null when the agent proposes nothing - there is then nothing to approve. */
  planId: string | null;
  instruction: string;
  narrative: string;
  steps: AgentStep[];
}

export interface AgentExecution {
  completed: number;
  total: number;
  results: string[];
  /** null on success. Steps run in order and stop at the first failure. */
  failure: string | null;
}

export function planWithAgent(instruction: string) {
  return api.post<AgentPlan>("/admin/agent/plan", { instruction });
}

export function executeAgentPlan(planId: string) {
  return api.post<AgentExecution>(`/admin/agent/plans/${planId}/execute`);
}
