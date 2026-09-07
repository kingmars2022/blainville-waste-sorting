<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "../useI18n";
import { useAuthStore, type AuthResponse } from "../stores/auth";
import { api, ApiError } from "../api/client";

const { t } = useI18n();
const auth = useAuthStore();
const router = useRouter();

const mode = ref<"login" | "register">("login");
const email = ref("");
const password = ref("");
const error = ref("");
const submitting = ref(false);

async function onSubmit() {
  error.value = "";
  submitting.value = true;

  try {
    const path = mode.value === "login" ? "/auth/login" : "/auth/register";
    const response = await api.post<AuthResponse>(path, { email: email.value, password: password.value });
    auth.setSession(response);
    router.push("/");
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : "Unexpected error";
  } finally {
    submitting.value = false;
  }
}

function toggleMode() {
  mode.value = mode.value === "login" ? "register" : "login";
  error.value = "";
}
</script>

<template>
  <section class="page">
    <h1>{{ mode === "login" ? t("auth.loginTitle") : t("auth.registerTitle") }}</h1>

    <form class="auth-form" @submit.prevent="onSubmit">
      <label>
        {{ t("auth.email") }}
        <input v-model="email" type="email" required autocomplete="email" />
      </label>
      <label>
        {{ t("auth.password") }}
        <input v-model="password" type="password" required minlength="8" autocomplete="current-password" />
      </label>

      <p v-if="error" class="auth-error">{{ error }}</p>

      <button type="submit" :disabled="submitting">
        {{ mode === "login" ? t("auth.submitLogin") : t("auth.submitRegister") }}
      </button>
    </form>

    <button type="button" class="link-button" @click="toggleMode">
      {{ mode === "login" ? t("auth.toggleToRegister") : t("auth.toggleToLogin") }}
    </button>
  </section>
</template>
