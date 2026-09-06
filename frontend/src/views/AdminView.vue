<script setup lang="ts">
import { useI18n } from "../useI18n";

const { t } = useI18n();

const summaryCards = [
  { key: "schedule", value: "3", status: "pending" },
  { key: "sorting", value: "10", status: "pending" },
  { key: "translations", value: "3", status: "complete" },
  { key: "notices", value: "0", status: "pending" }
] as const;

const recentRows = [
  { date: "2026-08-27", sector: "all", type: "organic", bin: "brown" },
  { date: "2026-09-01", sector: "south", type: "recycling", bin: "blue" },
  { date: "2026-09-02", sector: "north", type: "recycling", bin: "blue" }
];
</script>

<template>
  <section class="page">
    <div class="admin-hero">
      <span>{{ t("admin.adminOnly") }}</span>
      <h1>{{ t("admin.title") }}</h1>
      <p>{{ t("admin.subtitle") }}</p>
    </div>

    <div class="admin-summary">
      <article v-for="card in summaryCards" :key="card.key" class="admin-stat">
        <span>{{ t(`admin.${card.key}`) }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ t(`admin.${card.status}`) }}</small>
      </article>
    </div>

    <div class="admin-layout">
      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.schedule") }}</h2>
          <button type="button">{{ t("admin.addEvent") }}</button>
        </div>

        <div class="admin-form-grid">
          <label>
            {{ t("admin.date") }}
            <input type="date" />
          </label>
          <label>
            {{ t("admin.sector") }}
            <select>
              <option value="north">north</option>
              <option value="south">south</option>
              <option value="all">all</option>
            </select>
          </label>
          <label>
            {{ t("admin.type") }}
            <select>
              <option value="organic">organic</option>
              <option value="recycling">recycling</option>
              <option value="garbage">garbage</option>
              <option value="bulky">bulky</option>
              <option value="special">special</option>
            </select>
          </label>
          <label>
            {{ t("admin.bin") }}
            <select>
              <option value="brown">brown</option>
              <option value="blue">blue</option>
              <option value="black">black</option>
              <option value="none">none</option>
            </select>
          </label>
        </div>

        <table class="admin-table">
          <thead>
            <tr>
              <th>{{ t("admin.date") }}</th>
              <th>{{ t("admin.sector") }}</th>
              <th>{{ t("admin.type") }}</th>
              <th>{{ t("admin.bin") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in recentRows" :key="`${row.date}-${row.sector}-${row.type}`">
              <td>{{ row.date }}</td>
              <td>{{ row.sector }}</td>
              <td>{{ row.type }}</td>
              <td>{{ row.bin }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.sorting") }}</h2>
          <button type="button">{{ t("admin.addItem") }}</button>
        </div>

        <label>
          {{ t("admin.nameFr") }}
          <input type="text" placeholder="Boite a pizza" />
        </label>
        <label>
          {{ t("admin.nameEn") }}
          <input type="text" placeholder="Pizza box" />
        </label>
        <label>
          {{ t("admin.nameZh") }}
          <input type="text" placeholder="披萨盒" />
        </label>
        <label>
          {{ t("admin.instructionFr") }}
          <textarea rows="4" placeholder="Deposez cet article dans le bac brun."></textarea>
        </label>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.translations") }}</h2>
          <button type="button">{{ t("admin.reviewTranslations") }}</button>
        </div>
        <p class="admin-note">
          fr, en, zh doivent toujours etre remplis avant publication.
        </p>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.notices") }}</h2>
          <button type="button">{{ t("admin.publishNotice") }}</button>
        </div>
        <label>
          {{ t("admin.noticeTitle") }}
          <input type="text" />
        </label>
        <label>
          {{ t("admin.noticeBody") }}
          <textarea rows="4"></textarea>
        </label>
      </section>
    </div>
  </section>
</template>
