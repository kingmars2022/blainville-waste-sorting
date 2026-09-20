<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "../useI18n";
import { usePreferenceStore } from "../stores/preferences";
import { useAuthStore } from "../stores/auth";
import { api } from "../api/client";
import type { Language } from "../i18n/messages";
import type { Sector } from "../stores/preferences";

type BackendPreference = {
  sector: Sector;
  languageCode: Language;
  reminderEnabled: boolean;
};

const { t } = useI18n();
const preferences = usePreferenceStore();
const auth = useAuthStore();
const synced = ref(false);

onMounted(async () => {
  if (!auth.isAuthenticated) {
    return;
  }

  try {
    const remote = await api.get<BackendPreference>("/preferences");
    preferences.setLanguage(remote.languageCode);
    preferences.setSector(remote.sector);
    preferences.setRemindersEnabled(remote.reminderEnabled);
    synced.value = true;
  } catch {
    synced.value = false;
  }
});

async function persistToBackend() {
  if (!auth.isAuthenticated) {
    return;
  }

  await api.put<BackendPreference>("/preferences", {
    sector: preferences.sector,
    languageCode: preferences.language,
    reminderEnabled: preferences.remindersEnabled
  });
  synced.value = true;
}

function onLanguageChange(event: Event) {
  preferences.setLanguage((event.target as HTMLSelectElement).value as Language);
  persistToBackend();
}

function onSectorChange(event: Event) {
  preferences.setSector((event.target as HTMLSelectElement).value as Sector);
  persistToBackend();
}

function onReminderChange(event: Event) {
  preferences.setRemindersEnabled((event.target as HTMLInputElement).checked);
  persistToBackend();
}
</script>

<template>
  <section class="page">
    <h1>{{ t("settings.title") }}</h1>

    <p class="admin-note">
      {{ auth.isAuthenticated ? t("settings.synced") : t("settings.signInToSync") }}
    </p>

    <label>
      {{ t("settings.language") }}
      <select :value="preferences.language" @change="onLanguageChange">
        <option value="fr">Français</option>
        <option value="en">English</option>
        <option value="zh">中文</option>
      </select>
    </label>

    <label>
      {{ t("settings.sector") }}
      <select :value="preferences.sector" @change="onSectorChange">
        <option value="north">{{ t("settings.north") }}</option>
        <option value="south">{{ t("settings.south") }}</option>
      </select>
    </label>

    <label class="checkbox">
      <input
        type="checkbox"
        :checked="preferences.remindersEnabled"
        @change="onReminderChange"
      />
      {{ t("settings.reminders") }}
    </label>

    <!--
      The toggle used to say only "Reminders", beside a stored reminder_time
      that nothing ever read. It has one real effect - whether city notices
      reach this resident's inbox - so it now says that, and says what it does
      not do.
    -->
    <p class="admin-note">{{ t("settings.remindersHelp") }}</p>
  </section>
</template>
