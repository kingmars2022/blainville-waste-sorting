<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useI18n } from "../useI18n";
import { api } from "../api/client";

type Notice = {
  id: number;
  startsOn: string;
  endsOn: string;
  titleFr: string;
  titleEn: string;
  titleZh: string;
  bodyFr: string;
  bodyEn: string;
  bodyZh: string;
  sourceUrl: string | null;
  active: boolean;
};

const { t, language } = useI18n();

const summaryCards = [
  { key: "schedule", value: "3", status: "pending" },
  { key: "sorting", value: "10", status: "pending" },
  { key: "translations", value: "3", status: "complete" }
] as const;

const recentRows = [
  { date: "2026-08-27", sector: "all", type: "organic", bin: "brown" },
  { date: "2026-09-01", sector: "south", type: "recycling", bin: "blue" },
  { date: "2026-09-02", sector: "north", type: "recycling", bin: "blue" }
];

const notices = ref<Notice[]>([]);
const loadError = ref("");

const draft = reactive({
  startsOn: "",
  endsOn: "",
  titleFr: "",
  titleEn: "",
  titleZh: "",
  bodyFr: "",
  bodyEn: "",
  bodyZh: ""
});

async function loadNotices() {
  try {
    notices.value = await api.get<Notice[]>("/admin/notices");
    loadError.value = "";
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : "Failed to load notices";
  }
}

async function createNotice() {
  await api.post<Notice>("/admin/notices", {
    startsOn: draft.startsOn,
    endsOn: draft.endsOn,
    titleFr: draft.titleFr,
    titleEn: draft.titleEn,
    titleZh: draft.titleZh,
    bodyFr: draft.bodyFr,
    bodyEn: draft.bodyEn,
    bodyZh: draft.bodyZh,
    sourceUrl: null,
    active: true
  });

  Object.assign(draft, {
    startsOn: "",
    endsOn: "",
    titleFr: "",
    titleEn: "",
    titleZh: "",
    bodyFr: "",
    bodyEn: "",
    bodyZh: ""
  });

  await loadNotices();
}

async function deleteNotice(id: number) {
  await api.delete(`/admin/notices/${id}`);
  await loadNotices();
}

function noticeTitle(notice: Notice) {
  return { fr: notice.titleFr, en: notice.titleEn, zh: notice.titleZh }[language.value];
}

onMounted(loadNotices);
</script>

<template>
  <section class="page">
    <div class="admin-hero">
      <span>{{ t("admin.adminOnly") }}</span>
      <h1>{{ t("admin.title") }}</h1>
      <p>{{ t("admin.subtitle") }}</p>
    </div>

    <p class="admin-note">{{ t("admin.prototypeNote") }}</p>

    <div class="admin-summary">
      <article v-for="card in summaryCards" :key="card.key" class="admin-stat">
        <span>{{ t(`admin.${card.key}`) }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ t(`admin.${card.status}`) }}</small>
      </article>
      <article class="admin-stat">
        <span>{{ t("admin.notices") }}</span>
        <strong>{{ notices.length }}</strong>
        <small>{{ t("admin.complete") }}</small>
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
        </div>

        <p v-if="loadError" class="auth-error">{{ loadError }}</p>

        <form class="admin-form-grid" @submit.prevent="createNotice">
          <label>
            {{ t("admin.startsOn") }}
            <input v-model="draft.startsOn" type="date" required />
          </label>
          <label>
            {{ t("admin.endsOn") }}
            <input v-model="draft.endsOn" type="date" required />
          </label>
          <label>
            {{ t("admin.nameFr") }}
            <input v-model="draft.titleFr" type="text" required />
          </label>
          <label>
            {{ t("admin.nameEn") }}
            <input v-model="draft.titleEn" type="text" required />
          </label>
          <label>
            {{ t("admin.nameZh") }}
            <input v-model="draft.titleZh" type="text" required />
          </label>
          <label>
            {{ t("admin.noticeBody") }} (FR)
            <textarea v-model="draft.bodyFr" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.noticeBody") }} (EN)
            <textarea v-model="draft.bodyEn" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.noticeBody") }} (ZH)
            <textarea v-model="draft.bodyZh" rows="2" required></textarea>
          </label>
          <button type="submit">{{ t("admin.publishNotice") }}</button>
        </form>

        <table class="admin-table" v-if="notices.length">
          <thead>
            <tr>
              <th>{{ t("admin.startsOn") }}</th>
              <th>{{ t("admin.endsOn") }}</th>
              <th>{{ t("admin.noticeTitle") }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="notice in notices" :key="notice.id">
              <td>{{ notice.startsOn }}</td>
              <td>{{ notice.endsOn }}</td>
              <td>{{ noticeTitle(notice) }}</td>
              <td>
                <button type="button" @click="deleteNotice(notice.id)">{{ t("admin.delete") }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="admin-note">{{ t("admin.noNotices") }}</p>
      </section>
    </div>
  </section>
</template>
