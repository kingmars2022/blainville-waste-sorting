<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from "vue-router";
import { useI18n } from "./useI18n";
import { useAuthStore } from "./stores/auth";

const { t } = useI18n();
const auth = useAuthStore();
const router = useRouter();

function onLogout() {
  auth.logout();
  router.push("/");
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <strong>{{ t("app.name") }}</strong>
      <nav>
        <RouterLink to="/">{{ t("nav.home") }}</RouterLink>
        <RouterLink to="/tri">{{ t("nav.sorting") }}</RouterLink>
        <RouterLink to="/parametres">{{ t("nav.settings") }}</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin">{{ t("nav.admin") }}</RouterLink>
        <RouterLink v-if="!auth.isAuthenticated" to="/connexion">{{ t("nav.login") }}</RouterLink>
        <button v-else type="button" class="link-button nav-logout" @click="onLogout">
          {{ t("nav.logout") }}
        </button>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>
