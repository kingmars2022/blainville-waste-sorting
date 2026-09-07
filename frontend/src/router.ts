import { createRouter, createWebHistory } from "vue-router";
import HomeView from "./views/HomeView.vue";
import SettingsView from "./views/SettingsView.vue";
import SortingView from "./views/SortingView.vue";
import AdminView from "./views/AdminView.vue";
import LoginView from "./views/LoginView.vue";
import { useAuthStore } from "./stores/auth";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: HomeView },
    { path: "/tri", component: SortingView },
    { path: "/parametres", component: SettingsView },
    { path: "/admin", component: AdminView, meta: { requiresAdmin: true } },
    { path: "/connexion", component: LoginView }
  ]
});

router.beforeEach((to) => {
  if (to.meta.requiresAdmin) {
    const auth = useAuthStore();
    if (!auth.isAdmin) {
      return { path: "/connexion" };
    }
  }
});
