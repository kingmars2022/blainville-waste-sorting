import { defineStore } from "pinia";

export type Role = "USER" | "ADMIN";

export type AuthResponse = {
  token: string;
  userId: number;
  email: string;
  role: Role;
};

type AuthState = {
  token: string | null;
  userId: number | null;
  email: string | null;
  role: Role | null;
};

const storageKey = "bienvenue-blainville.auth";

function loadAuth(): AuthState {
  const fallback: AuthState = { token: null, userId: null, email: null, role: null };

  const raw = localStorage.getItem(storageKey);
  if (!raw) {
    return fallback;
  }

  try {
    return { ...fallback, ...JSON.parse(raw) };
  } catch {
    return fallback;
  }
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => loadAuth(),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    isAdmin: (state) => state.role === "ADMIN"
  },
  actions: {
    setSession(auth: AuthResponse) {
      this.token = auth.token;
      this.userId = auth.userId;
      this.email = auth.email;
      this.role = auth.role;
      localStorage.setItem(storageKey, JSON.stringify(this.$state));
    },
    logout() {
      this.token = null;
      this.userId = null;
      this.email = null;
      this.role = null;
      localStorage.removeItem(storageKey);
    }
  }
});
