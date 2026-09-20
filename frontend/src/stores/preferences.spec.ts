import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { usePreferenceStore } from "./preferences";

const STORAGE_KEY = "bienvenue-blainville.preferences";

/**
 * Language and sector decide what every page shows, and they are read from
 * localStorage before anything is rendered — so what happens when that value
 * is not what the app expects is a startup question, not an edge case.
 */
describe("preference store", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("starts French, north, reminders on", () => {
    const preferences = usePreferenceStore();

    expect(preferences.language).toBe("fr");
    expect(preferences.sector).toBe("north");
    expect(preferences.remindersEnabled).toBe(true);
  });

  it("persists each change so a reload keeps it", () => {
    const preferences = usePreferenceStore();
    preferences.setLanguage("zh");
    preferences.setSector("south");
    preferences.setRemindersEnabled(false);

    setActivePinia(createPinia());
    const reloaded = usePreferenceStore();

    expect(reloaded.language).toBe("zh");
    expect(reloaded.sector).toBe("south");
    expect(reloaded.remindersEnabled).toBe(false);
  });

  it("fills in anything a stored value is missing", () => {
    // A preference written by an older version of the app, before a field
    // existed. Reading it must not leave that field undefined.
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ language: "en" }));

    const preferences = usePreferenceStore();

    expect(preferences.language).toBe("en");
    expect(preferences.sector).toBe("north");
    expect(preferences.remindersEnabled).toBe(true);
  });

  it("falls back to the defaults when the stored value is not readable", () => {
    // Anything can end up in localStorage: a half-written value, a key reused
    // by something else, a browser extension. The auth store already guards
    // against it; this one did not, and an unparseable string took the whole
    // application down at startup rather than costing a resident their
    // language setting.
    localStorage.setItem(STORAGE_KEY, "{not json");

    expect(() => usePreferenceStore()).not.toThrow();
    expect(usePreferenceStore().language).toBe("fr");
  });
});
