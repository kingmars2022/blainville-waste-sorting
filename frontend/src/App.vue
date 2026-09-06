<script setup lang="ts">
import { RouterLink, RouterView } from "vue-router";
import { useI18n } from "./useI18n";

const { t } = useI18n();
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <strong>{{ t("app.name") }}</strong>
      <nav>
        <RouterLink to="/">{{ t("nav.home") }}</RouterLink>
        <RouterLink to="/tri">{{ t("nav.sorting") }}</RouterLink>
        <RouterLink to="/parametres">{{ t("nav.settings") }}</RouterLink>
        <RouterLink to="/admin">{{ t("nav.admin") }}</RouterLink>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>

