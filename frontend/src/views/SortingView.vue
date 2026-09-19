<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "../useI18n";
import {
  fetchSortingGuide,
  keywordsFor,
  translationFor,
  type DestinationType,
  type SortingItem
} from "../api/sortingGuide";
import { askAssistant, type AssistantAnswer } from "../api/assistant";
import { askAboutPhoto, type PhotoAnswer } from "../api/photo";
import { ApiError } from "../api/client";

const { language, t } = useI18n();
const query = ref("");

/**
 * The guide, from the API.
 *
 * <p>This used to be a TypeScript file bundled with the frontend, which meant
 * an administrator could correct an entry and the page residents actually
 * read would not change. One source of truth now, and it is the database the
 * assistant and the admin console already used.
 */
const items = ref<SortingItem[]>([]);
const guideError = ref("");

onMounted(async () => {
  try {
    items.value = await fetchSortingGuide();
  } catch {
    guideError.value = t("sorting.loadFailed");
  }
});

const assistantQuestion = ref("");
const assistantAnswer = ref<AssistantAnswer | null>(null);
const assistantError = ref("");
const assistantPending = ref(false);
let requestVersion = 0;

watch(language, () => {
  requestVersion++;
  assistantAnswer.value = null;
  assistantError.value = "";
  assistantPending.value = false;
}, { flush: "sync" });

const MAX_PHOTO_BYTES = 10_000_000;
const ALLOWED_PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];

const photoAnswer = ref<PhotoAnswer | null>(null);
const photoError = ref("");
const photoStage = ref<"" | "uploading" | "identifying">("");

watch(language, () => {
  photoAnswer.value = null;
  photoError.value = "";
});

async function onPhotoChosen(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // Reset immediately, so choosing the same file twice still fires a change.
  input.value = "";
  if (!file) {
    return;
  }

  // Checked here as well as on the server, purely so the resident hears about
  // it before a needless round trip. The server's check is the real one.
  if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
    photoError.value = t("photo.wrongType");
    return;
  }
  if (file.size > MAX_PHOTO_BYTES) {
    photoError.value = t("photo.tooBig");
    return;
  }

  photoError.value = "";
  photoAnswer.value = null;
  const version = ++requestVersion;

  try {
    photoStage.value = "uploading";
    const result = await askAboutPhoto(file, language.value, () => {
      // Upload done, identification starting. Without this the button said
      // "uploading" for the whole call, including the slowest part.
      if (version === requestVersion) {
        photoStage.value = "identifying";
      }
    });
    if (version === requestVersion) {
      photoAnswer.value = result.answer;
    }
  } catch (error) {
    if (version !== requestVersion) return;
    photoError.value =
      error instanceof ApiError && error.status === 429
        ? t("assistant.rateLimited")
        : t("photo.error");
  } finally {
    if (version === requestVersion) photoStage.value = "";
  }
}

async function ask() {
  const question = assistantQuestion.value.trim();
  if (!question || assistantPending.value) {
    return;
  }

  assistantPending.value = true;
  assistantError.value = "";
  assistantAnswer.value = null;
  const version = ++requestVersion;

  try {
    const answer = await askAssistant(question, language.value);
    if (version === requestVersion) assistantAnswer.value = answer;
  } catch (error) {
    if (version !== requestVersion) return;
    // 429 is the endpoint's own per-IP cap and deserves its own wording -
    // "try again in a moment" is actionable, "unavailable" is not.
    assistantError.value =
      error instanceof ApiError && error.status === 429
        ? t("assistant.rateLimited")
        : t("assistant.error");
  } finally {
    if (version === requestVersion) assistantPending.value = false;
  }
}
const activeDestination = ref<DestinationType | "all">("all");

const destinations: Array<DestinationType | "all"> = ["all", "organic", "recycling", "garbage", "ecocentre"];

// Entries with seasonal or on-request wording in the current language: the
// "when and how" cards at the top of the page.
const specialReminderItems = computed(() =>
  items.value.filter((item) => translationFor(item, language.value)?.availability)
);

const filteredItems = computed(() => {
  const normalizedQuery = query.value.trim().toLocaleLowerCase();

  return items.value.filter((item) => {
    const matchesDestination =
      activeDestination.value === "all" || item.destinationType === activeDestination.value;

    const translation = translationFor(item, language.value);
    if (!translation) {
      // Not translated into this language yet. Hidden rather than shown in
      // another one, so the gap is visible as a gap.
      return false;
    }

    const searchableText = [
      translation.name,
      translation.instruction,
      ...translation.examples,
      ...keywordsFor(item, language.value)
    ]
      .join(" ")
      .toLocaleLowerCase();

    const matchesQuery = normalizedQuery.length === 0 || searchableText.includes(normalizedQuery);
    return matchesDestination && matchesQuery;
  });
});

function destinationLabel(destination: DestinationType | "all") {
  if (destination === "all") {
    return t("sorting.all");
  }

  return t(`destination.${destination}`);
}
</script>

<template>
  <section class="page">
    <h1>{{ t("sorting.title") }}</h1>

    <section class="assistant">
      <h2>{{ t("assistant.title") }}</h2>
      <p class="assistant-hint">{{ t("assistant.hint") }}</p>

      <form class="assistant-form" @submit.prevent="ask">
        <input
          v-model="assistantQuestion"
          class="search"
          type="text"
          maxlength="300"
          :placeholder="t('assistant.placeholder')"
        />
        <button type="submit" :disabled="assistantPending || !assistantQuestion.trim()">
          {{ assistantPending ? t("assistant.asking") : t("assistant.ask") }}
        </button>
      </form>

      <div class="photo-ask">
        <h3>{{ t("photo.title") }}</h3>
        <p class="assistant-hint">{{ t("photo.hint") }}</p>
        <label class="photo-button" :class="{ busy: photoStage }">
          <!--
            capture="environment" asks a phone for the rear camera directly,
            which is the whole point on the device this feature is for.
          -->
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            capture="environment"
            :disabled="!!photoStage"
            @change="onPhotoChosen"
          />
          <span v-if="photoStage === 'uploading'">{{ t("photo.uploading") }}</span>
          <span v-else-if="photoStage === 'identifying'">{{ t("photo.identifying") }}</span>
          <span v-else>{{ t("photo.choose") }}</span>
        </label>

        <p v-if="photoError" class="assistant-error">{{ photoError }}</p>

        <article
          v-else-if="photoAnswer"
          class="assistant-answer"
          :class="{ ungrounded: !photoAnswer.grounded }"
        >
          <p v-if="photoAnswer.identifiedAs" class="photo-identified">
            {{ t("photo.identifiedAs") }}: <strong>{{ photoAnswer.identifiedAs }}</strong>
          </p>
          <p>{{ photoAnswer.answer }}</p>

          <p v-if="photoAnswer.sources.length" class="assistant-sources">
            <strong>{{ t("assistant.sources") }}:</strong>
            <a
              v-for="source in photoAnswer.sources"
              :key="source.itemId"
              :href="source.sourceUrl ?? undefined"
              target="_blank"
              rel="noreferrer"
            >{{ source.name }}</a>
          </p>

          <p class="assistant-provider">
            {{ t("assistant.poweredBy") }} {{ photoAnswer.provider }}
          </p>
        </article>
      </div>

      <p v-if="assistantError" class="assistant-error">{{ assistantError }}</p>

      <article
        v-else-if="assistantAnswer"
        class="assistant-answer"
        :class="{ ungrounded: !assistantAnswer.grounded }"
      >
        <p>{{ assistantAnswer.answer }}</p>

        <!--
          Sources are shown, not hidden behind a disclosure. An answer a
          resident cannot trace back to the municipal guide is worth less than
          one they can check, and showing them is also what makes a wrong
          retrieval obvious instead of invisible.
        -->
        <p v-if="assistantAnswer.sources.length" class="assistant-sources">
          <strong>{{ t("assistant.sources") }}:</strong>
          <a
            v-for="source in assistantAnswer.sources"
            :key="source.itemId"
            :href="source.sourceUrl ?? undefined"
            target="_blank"
            rel="noreferrer"
          >{{ source.name }}</a>
        </p>
        <p v-else class="assistant-sources">{{ t("assistant.notFound") }}</p>

        <p class="assistant-provider">
          {{ t("assistant.poweredBy") }} {{ assistantAnswer.provider }}
        </p>
      </article>
    </section>

    <section class="special-reminders">
      <h2>{{ t("sorting.specialReminders") }}</h2>
      <div class="reminder-list">
        <article v-for="item in specialReminderItems" :key="item.id" class="reminder-card">
          <span>{{ destinationLabel(item.destinationType) }}</span>
          <strong>{{ translationFor(item, language)?.name }}</strong>
          <p>{{ translationFor(item, language)?.availability }}</p>
        </article>
      </div>
    </section>

    <input v-model="query" class="search" type="search" :placeholder="t('sorting.search')" />

    <div class="segmented" aria-label="Sorting filters">
      <button
        v-for="destination in destinations"
        :key="destination"
        type="button"
        :class="{ active: activeDestination === destination }"
        @click="activeDestination = destination"
      >
        {{ destinationLabel(destination) }}
      </button>
    </div>

    <p v-if="guideError" class="assistant-error">{{ guideError }}</p>

    <div v-else-if="filteredItems.length === 0" class="empty">
      {{ t("sorting.noResults") }}
    </div>

    <div class="sorting-grid">
      <article
        v-for="item in filteredItems"
        :key="item.id"
        class="sorting-card"
        :class="item.binColor"
      >
        <div>
          <span class="tag">{{ destinationLabel(item.destinationType) }}</span>
          <h2>{{ translationFor(item, language)?.name }}</h2>
          <p>{{ translationFor(item, language)?.instruction }}</p>
        </div>

        <div v-if="translationFor(item, language)?.examples.length">
          <strong>{{ t("sorting.examples") }}</strong>
          <ul>
            <li
              v-for="example in translationFor(item, language)?.examples"
              :key="example"
            >{{ example }}</li>
          </ul>
        </div>

        <div v-if="translationFor(item, language)?.availability" class="availability">
          <strong>{{ t("sorting.availability") }}</strong>
          <p>{{ translationFor(item, language)?.availability }}</p>
        </div>

        <div v-if="translationFor(item, language)?.location" class="availability">
          <strong>{{ t("sorting.location") }}</strong>
          <p>{{ translationFor(item, language)?.location }}</p>
        </div>

        <a
          v-if="item.sourceUrl"
          :href="item.sourceUrl"
          target="_blank"
          rel="noreferrer"
        >{{ t("sorting.source") }}</a>
      </article>
    </div>
  </section>
</template>
