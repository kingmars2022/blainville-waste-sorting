import { api } from "./client";
import type { Language } from "../i18n/messages";

export type DestinationType = "organic" | "recycling" | "garbage" | "ecocentre" | "bulky" | "special";
export type BinColor = "brown" | "blue" | "black" | "none";

export interface SortingTranslation {
  name: string;
  instruction: string;
  location: string | null;
  availability: string | null;
  examples: string[];
}

export interface SortingItem {
  id: number;
  destinationType: DestinationType;
  binColor: BinColor;
  sourceUrl: string | null;
  fr: SortingTranslation | null;
  en: SortingTranslation | null;
  zh: SortingTranslation | null;
  keywordsFr: string[];
  keywordsEn: string[];
  keywordsZh: string[];
}

export function fetchSortingGuide() {
  return api.get<SortingItem[]>("/sorting-items");
}

/**
 * The translation for a language, or null when an entry has not been
 * translated yet.
 *
 * <p>Returning null rather than falling back to French is deliberate: a card
 * silently rendered in the wrong language looks like a translation and is not
 * one, and an administrator has no way to notice the gap.
 */
export function translationFor(item: SortingItem, language: Language): SortingTranslation | null {
  return language === "en" ? item.en : language === "zh" ? item.zh : item.fr;
}

export function keywordsFor(item: SortingItem, language: Language): string[] {
  return language === "en" ? item.keywordsEn : language === "zh" ? item.keywordsZh : item.keywordsFr;
}
