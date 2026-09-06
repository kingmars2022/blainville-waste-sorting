<script setup lang="ts">
import { useI18n } from "../useI18n";

const { t } = useI18n();
</script>

<template>
  <section class="page">
    <div class="notice">
      <p class="eyebrow">{{ t("home.tomorrow") }}</p>
      <h1>{{ t("bin.brown") }}</h1>
      <p>{{ t("collection.organic") }}</p>
      <p>{{ t("home.putOut") }}</p>
    </div>

    <section class="panel">
      <h2>{{ t("home.title") }}</h2>
      <div class="bin-list">
        <article class="bin-card brown">
          <span>{{ t("bin.brown") }}</span>
          <strong>{{ t("collection.organic") }}</strong>
        </article>
        <article class="bin-card blue">
          <span>{{ t("bin.blue") }}</span>
          <strong>{{ t("collection.recycling") }}</strong>
        </article>
        <article class="bin-card black">
          <span>{{ t("bin.black") }}</span>
          <strong>{{ t("collection.garbage") }}</strong>
        </article>
      </div>
    </section>
  </section>
</template>

