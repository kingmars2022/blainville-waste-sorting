/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080"
    }
  },
  test: {
    // happy-dom rather than jsdom: these tests need localStorage, fetch and
    // enough DOM to mount a component, and it starts in a fraction of the
    // time.
    environment: "happy-dom",
    include: ["src/**/*.spec.ts"],
    restoreMocks: true
  }
});

