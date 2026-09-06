import { createRouter, createWebHistory } from "vue-router";
import HomeView from "./views/HomeView.vue";
import SettingsView from "./views/SettingsView.vue";
import SortingView from "./views/SortingView.vue";
import AdminView from "./views/AdminView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: HomeView },
    { path: "/tri", component: SortingView },
    { path: "/parametres", component: SettingsView },
    { path: "/admin", component: AdminView }
  ]
});

