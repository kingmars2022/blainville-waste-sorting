<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "../useI18n";
import { sortingGuide, type DestinationType } from "../data/sortingGuide";

const { language, t } = useI18n();
const query = ref("");
const activeDestination = ref<DestinationType | "all">("all");

const destinations: Array<DestinationType | "all"> = ["all", "organic", "recycling", "garbage", "ecocentre"];

const specialReminderItems = computed(() => sortingGuide.filter((item) => item.availability));

const filteredItems = computed(() => {
  const normalizedQuery = query.value.trim().toLocaleLowerCase();

  return sortingGuide.filter((item) => {
    const matchesDestination =
      activeDestination.value === "all" || item.destination === activeDestination.value;

    const searchableText = [
      item.names[language.value],
      item.instruction[language.value],
      ...item.examples[language.value],
      ...item.keywords[language.value]
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

    <section class="special-reminders">
      <h2>{{ t("sorting.specialReminders") }}</h2>
      <div class="reminder-list">
        <article v-for="item in specialReminderItems" :key="item.id" class="reminder-card">
          <span>{{ destinationLabel(item.destination) }}</span>
          <strong>{{ item.names[language] }}</strong>
          <p>{{ item.availability?.[language] }}</p>
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

    <div v-if="filteredItems.length === 0" class="empty">
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
          <span class="tag">{{ destinationLabel(item.destination) }}</span>
          <h2>{{ item.names[language] }}</h2>
          <p>{{ item.instruction[language] }}</p>
        </div>

        <div>
          <strong>{{ t("sorting.examples") }}</strong>
          <ul>
            <li v-for="example in item.examples[language]" :key="example">{{ example }}</li>
          </ul>
        </div>

        <div v-if="item.availability" class="availability">
          <strong>{{ t("sorting.availability") }}</strong>
          <p>{{ item.availability[language] }}</p>
        </div>

        <div v-if="item.location" class="availability">
          <strong>{{ t("sorting.location") }}</strong>
          <p>{{ item.location[language] }}</p>
        </div>

        <a :href="item.sourceUrl" target="_blank" rel="noreferrer">{{ t("sorting.source") }}</a>
      </article>
    </div>
  </section>
</template>
