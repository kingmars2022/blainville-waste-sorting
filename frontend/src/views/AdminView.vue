<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "../useI18n";
import { api, ApiError } from "../api/client";
import { executeAgentPlan, planWithAgent, type AgentExecution, type AgentPlan } from "../api/agent";

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

type CollectionEvent = {
  id: number;
  collectionDate: string;
  sector: string;
  collectionType: string;
  binColor: string;
  noteFr: string | null;
  noteEn: string | null;
  noteZh: string | null;
  sourceUrl: string | null;
};

type Translation = { name: string; instruction: string; location: string | null };

type SortingItem = {
  id: number;
  destinationType: string;
  binColor: string;
  sourceUrl: string | null;
  fr: Translation;
  en: Translation;
  zh: Translation;
  keywordsFr: string[];
  keywordsEn: string[];
  keywordsZh: string[];
};

const { t, language } = useI18n();

const agentInstruction = ref("");
const agentPlan = ref<AgentPlan | null>(null);
const agentExecution = ref<AgentExecution | null>(null);
const agentError = ref("");
const agentPlanning = ref(false);
const agentApplying = ref(false);

async function proposePlan() {
  const instruction = agentInstruction.value.trim();
  if (!instruction || agentPlanning.value) {
    return;
  }

  agentPlanning.value = true;
  agentError.value = "";
  agentPlan.value = null;
  agentExecution.value = null;

  try {
    agentPlan.value = await planWithAgent(instruction);
  } catch (error) {
    // 503 means no API key is configured. That is not a failure to apologise
    // for - it is a setup step, and the forms below already do the job.
    agentError.value =
      error instanceof ApiError && error.status === 503
        ? t("agent.unavailable")
        : error instanceof ApiError
          ? error.message
          : t("agent.error");
  } finally {
    agentPlanning.value = false;
  }
}

async function approvePlan() {
  const planId = agentPlan.value?.planId;
  if (!planId || agentApplying.value) {
    return;
  }

  agentApplying.value = true;
  agentError.value = "";

  try {
    agentExecution.value = await executeAgentPlan(planId);
    // The plan is single-use on the server, so it must not stay approvable here.
    agentPlan.value = null;
    await Promise.all([loadNotices(), loadEvents()]);
  } catch (error) {
    agentError.value = error instanceof ApiError ? error.message : t("agent.error");
  } finally {
    agentApplying.value = false;
  }
}

function discardPlan() {
  agentPlan.value = null;
  agentExecution.value = null;
  agentError.value = "";
}

const notices = ref<Notice[]>([]);
const noticeError = ref("");

const events = ref<CollectionEvent[]>([]);
const eventError = ref("");

const sortingItems = ref<SortingItem[]>([]);
const sortingError = ref("");

const summaryCards = computed(() => [
  { key: "schedule" as const, value: events.value.length },
  { key: "sorting" as const, value: sortingItems.value.length },
  { key: "notices" as const, value: notices.value.length }
]);

// --- Notices -----------------------------------------------------------

const noticeDraft = reactive({
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
    noticeError.value = "";
  } catch (err) {
    noticeError.value = err instanceof Error ? err.message : "Failed to load notices";
  }
}

async function createNotice() {
  await api.post<Notice>("/admin/notices", {
    startsOn: noticeDraft.startsOn,
    endsOn: noticeDraft.endsOn,
    titleFr: noticeDraft.titleFr,
    titleEn: noticeDraft.titleEn,
    titleZh: noticeDraft.titleZh,
    bodyFr: noticeDraft.bodyFr,
    bodyEn: noticeDraft.bodyEn,
    bodyZh: noticeDraft.bodyZh,
    sourceUrl: null,
    active: true
  });

  Object.assign(noticeDraft, {
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

// --- Collection schedule -------------------------------------------------

const eventDraft = reactive({
  collectionDate: "",
  sector: "all",
  collectionType: "organic",
  binColor: "brown"
});

async function loadEvents() {
  try {
    events.value = await api.get<CollectionEvent[]>("/admin/collections");
    eventError.value = "";
  } catch (err) {
    eventError.value = err instanceof Error ? err.message : "Failed to load collections";
  }
}

async function createEvent() {
  await api.post<CollectionEvent>("/admin/collections", {
    collectionDate: eventDraft.collectionDate,
    sector: eventDraft.sector,
    collectionType: eventDraft.collectionType,
    binColor: eventDraft.binColor,
    noteFr: null,
    noteEn: null,
    noteZh: null,
    sourceUrl: null
  });

  eventDraft.collectionDate = "";
  await loadEvents();
}

async function deleteEvent(id: number) {
  await api.delete(`/admin/collections/${id}`);
  await loadEvents();
}

// --- Sorting items ---------------------------------------------------------

const sortingDraft = reactive({
  destinationType: "organic",
  binColor: "brown",
  sourceUrl: "",
  nameFr: "",
  nameEn: "",
  nameZh: "",
  instructionFr: "",
  instructionEn: "",
  instructionZh: "",
  locationFr: "",
  locationEn: "",
  locationZh: "",
  keywordsFr: "",
  keywordsEn: "",
  keywordsZh: ""
});

async function loadSortingItems() {
  try {
    sortingItems.value = await api.get<SortingItem[]>("/admin/sorting-items");
    sortingError.value = "";
  } catch (err) {
    sortingError.value = err instanceof Error ? err.message : "Failed to load sorting items";
  }
}

function splitKeywords(value: string) {
  return value
    .split(",")
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0);
}

async function createSortingItem() {
  await api.post<SortingItem>("/admin/sorting-items", {
    destinationType: sortingDraft.destinationType,
    binColor: sortingDraft.binColor,
    sourceUrl: sortingDraft.sourceUrl || null,
    fr: { name: sortingDraft.nameFr, instruction: sortingDraft.instructionFr, location: sortingDraft.locationFr || null },
    en: { name: sortingDraft.nameEn, instruction: sortingDraft.instructionEn, location: sortingDraft.locationEn || null },
    zh: { name: sortingDraft.nameZh, instruction: sortingDraft.instructionZh, location: sortingDraft.locationZh || null },
    keywordsFr: splitKeywords(sortingDraft.keywordsFr),
    keywordsEn: splitKeywords(sortingDraft.keywordsEn),
    keywordsZh: splitKeywords(sortingDraft.keywordsZh)
  });

  Object.assign(sortingDraft, {
    sourceUrl: "",
    nameFr: "",
    nameEn: "",
    nameZh: "",
    instructionFr: "",
    instructionEn: "",
    instructionZh: "",
    locationFr: "",
    locationEn: "",
    locationZh: "",
    keywordsFr: "",
    keywordsEn: "",
    keywordsZh: ""
  });

  await loadSortingItems();
}

async function deleteSortingItem(id: number) {
  await api.delete(`/admin/sorting-items/${id}`);
  await loadSortingItems();
}

function sortingName(item: SortingItem) {
  return { fr: item.fr, en: item.en, zh: item.zh }[language.value].name;
}

onMounted(() => {
  loadNotices();
  loadEvents();
  loadSortingItems();
});
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
        <small>{{ t("admin.complete") }}</small>
      </article>
    </div>

    <section class="agent">
      <h2>{{ t("agent.title") }}</h2>
      <p class="agent-hint">{{ t("agent.hint") }}</p>

      <form class="agent-form" @submit.prevent="proposePlan">
        <textarea
          v-model="agentInstruction"
          rows="2"
          maxlength="1000"
          :placeholder="t('agent.placeholder')"
        ></textarea>
        <button type="submit" :disabled="agentPlanning || !agentInstruction.trim()">
          {{ agentPlanning ? t("agent.planning") : t("agent.plan") }}
        </button>
      </form>

      <p v-if="agentError" class="auth-error">{{ agentError }}</p>

      <!--
        A plan is a proposal. It is rendered as a list an administrator can
        read line by line, and the confirm button is the only thing that
        touches the database.
      -->
      <article v-if="agentPlan" class="agent-plan">
        <p class="agent-narrative">{{ agentPlan.narrative }}</p>

        <template v-if="agentPlan.steps.length">
          <strong>{{ t("agent.proposed") }}</strong>
          <ol class="agent-steps">
            <li v-for="(step, index) in agentPlan.steps" :key="index">
              <code>{{ step.tool }}</code>
              <span>{{ step.summary }}</span>
            </li>
          </ol>

          <div class="agent-actions">
            <button type="button" :disabled="agentApplying" @click="approvePlan">
              {{ agentApplying ? t("agent.applying") : t("agent.approve") }}
            </button>
            <button type="button" class="agent-secondary" @click="discardPlan">
              {{ t("agent.discard") }}
            </button>
          </div>
        </template>

        <p v-else class="agent-empty">{{ t("agent.nothingProposed") }}</p>
      </article>

      <article
        v-if="agentExecution"
        class="agent-result"
        :class="{ partial: agentExecution.failure }"
      >
        <strong>
          {{ agentExecution.failure ? t("agent.partial") : t("agent.applied") }}
          ({{ agentExecution.completed }}/{{ agentExecution.total }})
        </strong>
        <ul>
          <li v-for="(result, index) in agentExecution.results" :key="index">{{ result }}</li>
        </ul>
        <!-- Steps run in order and stop at the first failure, so saying which
             step failed is what tells an administrator what to fix. -->
        <p v-if="agentExecution.failure" class="agent-failure">{{ agentExecution.failure }}</p>
      </article>
    </section>

    <div class="admin-layout">
      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.schedule") }}</h2>
        </div>

        <p v-if="eventError" class="auth-error">{{ eventError }}</p>

        <form class="admin-form-grid" @submit.prevent="createEvent">
          <label>
            {{ t("admin.date") }}
            <input v-model="eventDraft.collectionDate" type="date" required />
          </label>
          <label>
            {{ t("admin.sector") }}
            <select v-model="eventDraft.sector">
              <option value="north">north</option>
              <option value="south">south</option>
              <option value="all">all</option>
            </select>
          </label>
          <label>
            {{ t("admin.type") }}
            <select v-model="eventDraft.collectionType">
              <option value="organic">organic</option>
              <option value="recycling">recycling</option>
              <option value="garbage">garbage</option>
              <option value="bulky">bulky</option>
              <option value="ecocentre">ecocentre</option>
              <option value="special">special</option>
            </select>
          </label>
          <label>
            {{ t("admin.bin") }}
            <select v-model="eventDraft.binColor">
              <option value="brown">brown</option>
              <option value="blue">blue</option>
              <option value="black">black</option>
              <option value="none">none</option>
            </select>
          </label>
          <button type="submit">{{ t("admin.addEvent") }}</button>
        </form>

        <table class="admin-table" v-if="events.length">
          <thead>
            <tr>
              <th>{{ t("admin.date") }}</th>
              <th>{{ t("admin.sector") }}</th>
              <th>{{ t("admin.type") }}</th>
              <th>{{ t("admin.bin") }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in events" :key="event.id">
              <td>{{ event.collectionDate }}</td>
              <td>{{ event.sector }}</td>
              <td>{{ event.collectionType }}</td>
              <td>{{ event.binColor }}</td>
              <td>
                <button type="button" @click="deleteEvent(event.id)">{{ t("admin.delete") }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="admin-note">{{ t("admin.noScheduleEvents") }}</p>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.sorting") }}</h2>
        </div>

        <p v-if="sortingError" class="auth-error">{{ sortingError }}</p>

        <form class="admin-form-grid" @submit.prevent="createSortingItem">
          <label>
            {{ t("admin.type") }}
            <select v-model="sortingDraft.destinationType">
              <option value="organic">organic</option>
              <option value="recycling">recycling</option>
              <option value="garbage">garbage</option>
              <option value="ecocentre">ecocentre</option>
              <option value="bulky">bulky</option>
              <option value="special">special</option>
            </select>
          </label>
          <label>
            {{ t("admin.bin") }}
            <select v-model="sortingDraft.binColor">
              <option value="brown">brown</option>
              <option value="blue">blue</option>
              <option value="black">black</option>
              <option value="none">none</option>
            </select>
          </label>
          <label>
            {{ t("admin.sourceUrl") }}
            <input v-model="sortingDraft.sourceUrl" type="url" />
          </label>

          <label>
            {{ t("admin.nameFr") }}
            <input v-model="sortingDraft.nameFr" type="text" required />
          </label>
          <label>
            {{ t("admin.instruction") }} (FR)
            <textarea v-model="sortingDraft.instructionFr" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.location") }} (FR)
            <input v-model="sortingDraft.locationFr" type="text" />
          </label>
          <label>
            {{ t("admin.keywords") }} (FR)
            <input v-model="sortingDraft.keywordsFr" type="text" />
          </label>

          <label>
            {{ t("admin.nameEn") }}
            <input v-model="sortingDraft.nameEn" type="text" required />
          </label>
          <label>
            {{ t("admin.instruction") }} (EN)
            <textarea v-model="sortingDraft.instructionEn" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.location") }} (EN)
            <input v-model="sortingDraft.locationEn" type="text" />
          </label>
          <label>
            {{ t("admin.keywords") }} (EN)
            <input v-model="sortingDraft.keywordsEn" type="text" />
          </label>

          <label>
            {{ t("admin.nameZh") }}
            <input v-model="sortingDraft.nameZh" type="text" required />
          </label>
          <label>
            {{ t("admin.instruction") }} (ZH)
            <textarea v-model="sortingDraft.instructionZh" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.location") }} (ZH)
            <input v-model="sortingDraft.locationZh" type="text" />
          </label>
          <label>
            {{ t("admin.keywords") }} (ZH)
            <input v-model="sortingDraft.keywordsZh" type="text" />
          </label>

          <button type="submit">{{ t("admin.addItem") }}</button>
        </form>

        <table class="admin-table" v-if="sortingItems.length">
          <thead>
            <tr>
              <th>{{ t("admin.type") }}</th>
              <th>{{ t("admin.bin") }}</th>
              <th>{{ t("admin.nameFr") }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in sortingItems" :key="item.id">
              <td>{{ item.destinationType }}</td>
              <td>{{ item.binColor }}</td>
              <td>{{ sortingName(item) }}</td>
              <td>
                <button type="button" @click="deleteSortingItem(item.id)">{{ t("admin.delete") }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="admin-note">{{ t("admin.noSortingItems") }}</p>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.translations") }}</h2>
        </div>
        <p class="admin-note">
          {{ sortingItems.length }} {{ t("admin.translationsSummary") }}
        </p>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-title">
          <h2>{{ t("admin.notices") }}</h2>
        </div>

        <p v-if="noticeError" class="auth-error">{{ noticeError }}</p>

        <form class="admin-form-grid" @submit.prevent="createNotice">
          <label>
            {{ t("admin.startsOn") }}
            <input v-model="noticeDraft.startsOn" type="date" required />
          </label>
          <label>
            {{ t("admin.endsOn") }}
            <input v-model="noticeDraft.endsOn" type="date" required />
          </label>
          <label>
            {{ t("admin.nameFr") }}
            <input v-model="noticeDraft.titleFr" type="text" required />
          </label>
          <label>
            {{ t("admin.nameEn") }}
            <input v-model="noticeDraft.titleEn" type="text" required />
          </label>
          <label>
            {{ t("admin.nameZh") }}
            <input v-model="noticeDraft.titleZh" type="text" required />
          </label>
          <label>
            {{ t("admin.noticeBody") }} (FR)
            <textarea v-model="noticeDraft.bodyFr" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.noticeBody") }} (EN)
            <textarea v-model="noticeDraft.bodyEn" rows="2" required></textarea>
          </label>
          <label>
            {{ t("admin.noticeBody") }} (ZH)
            <textarea v-model="noticeDraft.bodyZh" rows="2" required></textarea>
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
