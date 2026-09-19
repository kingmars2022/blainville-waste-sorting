import { api } from "./client";
import type { Language } from "../i18n/messages";

export interface AssistantSource {
  itemId: number;
  name: string;
  destinationType: string | null;
  binColor: string | null;
  sourceUrl: string | null;
  score: number;
}

export interface AssistantAnswer {
  answer: string;
  /** false when the guide had nothing relevant - the answer is then an explicit "I don't know". */
  grounded: boolean;
  /** "template" or "anthropic:<model>", so the UI never has to guess what answered. */
  provider: string;
  sources: AssistantSource[];
}

export function askAssistant(question: string, language: Language) {
  return api.post<AssistantAnswer>("/assistant/ask", { question, language });
}
