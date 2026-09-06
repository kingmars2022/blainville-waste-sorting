<script setup lang="ts">
import { useI18n } from "../useI18n";
import { usePreferenceStore } from "../stores/preferences";
import type { Language } from "../i18n/messages";
import type { Sector } from "../stores/preferences";

const { t } = useI18n();
const preferences = usePreferenceStore();

function onLanguageChange(event: Event) {
  preferences.setLanguage((event.target as HTMLSelectElement).value as Language);
}

function onSectorChange(event: Event) {
  preferences.setSector((event.target as HTMLSelectElement).value as Sector);
}

function onReminderChange(event: Event) {
  preferences.setRemindersEnabled((event.target as HTMLInputElement).checked);
}
</script>

<template>
  <section class="page">
    <h1>{{ t("settings.title") }}</h1>

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
  </section>
</template>
