import { defineStore } from "pinia";
import type { Language } from "../i18n/messages";

export type Sector = "north" | "south";

type PreferenceState = {
  language: Language;
  sector: Sector;
  remindersEnabled: boolean;
};

const storageKey = "bienvenue-blainville.preferences";

function loadPreferences(): PreferenceState {
  const fallback: PreferenceState = {
    language: "fr",
    sector: "north",
    remindersEnabled: true
  };

  const raw = localStorage.getItem(storageKey);
  if (!raw) {
    return fallback;
  }

  try {
    return { ...fallback, ...JSON.parse(raw) };
  } catch {
    // Anything can end up in localStorage: a half-written value, a key reused
    // by something else, an extension. This runs while the first view is being
    // created, so an unguarded parse took the whole application down at
    // startup - for a stored language setting. The auth store already guarded
    // against it; this one did not.
    return fallback;
  }
}

export const usePreferenceStore = defineStore("preferences", {
  state: (): PreferenceState => loadPreferences(),
  actions: {
    save() {
      localStorage.setItem(storageKey, JSON.stringify(this.$state));
    },
    setLanguage(language: Language) {
      this.language = language;
      this.save();
    },
    setSector(sector: Sector) {
      this.sector = sector;
      this.save();
    },
    setRemindersEnabled(enabled: boolean) {
      this.remindersEnabled = enabled;
      this.save();
    }
  }
});

