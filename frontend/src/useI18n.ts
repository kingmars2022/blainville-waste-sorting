import { computed } from "vue";
import { messages, type MessageKey } from "./i18n/messages";
import { usePreferenceStore } from "./stores/preferences";

export function useI18n() {
  const preferences = usePreferenceStore();
  const language = computed(() => preferences.language);

  function t(key: MessageKey) {
    return messages[language.value][key];
  }

  return { language, t };
}

