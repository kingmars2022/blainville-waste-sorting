import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import AdminView from "./AdminView.vue";

const get = vi.fn();
const post = vi.fn();
const put = vi.fn();
const del = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
    delete: (...args: unknown[]) => del(...args)
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  }
}));

vi.mock("../api/agent", () => ({
  planWithAgent: vi.fn(),
  executeAgentPlan: vi.fn()
}));

/**
 * The admin console can now correct an entry, not only create and delete one.
 *
 * What these tests are actually about is not the form appearing — it is what
 * an edit does to the fields nobody typed into. `SortingItemService.update`
 * replaces a translation wholesale, and the console's model of one predated
 * the migrations that moved `examples` and `availability` into the database.
 * A form that loaded three fields per language and sent three back would have
 * silently emptied every card's examples on the first edit.
 */
describe("AdminView editing", () => {
  const sortingItem = {
    id: 7,
    destinationType: "organic",
    binColor: "brown",
    sourceUrl: "https://blainville.ca/organics",
    fr: {
      name: "Restes de fruits",
      instruction: "Bac brun.",
      location: null,
      availability: "Toute l'annee",
      examples: ["fruits", "legumes", "pain"]
    },
    en: {
      name: "Fruit scraps",
      instruction: "Brown bin.",
      location: null,
      availability: "Year round",
      examples: ["fruit", "vegetables", "bread"]
    },
    zh: {
      name: "水果残渣",
      instruction: "棕桶。",
      location: null,
      availability: "全年",
      examples: ["水果", "蔬菜", "面包"]
    },
    keywordsFr: ["fruits", "legumes"],
    keywordsEn: ["fruit"],
    keywordsZh: ["水果"]
  };

  const collectionEvent = {
    id: 42,
    collectionDate: "2027-03-18",
    sector: "all",
    collectionType: "organic",
    binColor: "brown",
    noteFr: "Collecte du jeudi.",
    noteEn: "Thursday collection.",
    noteZh: "周四收集。",
    sourceUrl: "https://blainville.ca/organics"
  };

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    get.mockImplementation(async (path: string) => {
      if (path === "/admin/sorting-items") return [sortingItem];
      if (path === "/admin/collections") return [collectionEvent];
      return [];
    });
    post.mockResolvedValue({});
    put.mockResolvedValue({});
  });

  async function mountAdmin() {
    const wrapper = mount(AdminView);
    await flushPromises();
    return wrapper;
  }

  /** The panel whose heading matches, so the tests do not depend on order. */
  function panel(wrapper: ReturnType<typeof mount>, heading: string) {
    const found = wrapper
      .findAll(".admin-panel")
      .find((section) => section.find("h2").text() === heading);
    if (!found) throw new Error(`no panel headed "${heading}"`);
    return found;
  }

  const editButton = (section: ReturnType<typeof panel>) =>
    section.findAll("tbody button").find((button) => button.text() === "Modifier")!;

  const saveButton = (section: ReturnType<typeof panel>) =>
    section.findAll("form button").find((button) => button.text() === "Enregistrer")!;

  it("creates with POST while nothing is being edited", async () => {
    const wrapper = await mountAdmin();
    const schedule = panel(wrapper, "Calendrier des collectes");

    await schedule.find('input[type="date"]').setValue("2027-05-01");
    await schedule.find("form").trigger("submit");
    await flushPromises();

    expect(post).toHaveBeenCalledWith("/admin/collections", expect.objectContaining({
      collectionDate: "2027-05-01"
    }));
    expect(put).not.toHaveBeenCalled();
  });

  it("keeps every field of a sorting item that the edit did not touch", async () => {
    const wrapper = await mountAdmin();
    const sorting = panel(wrapper, "Articles de tri");

    await editButton(sorting).trigger("click");
    await flushPromises();

    // Only the French name changes. Everything else must come back unchanged.
    const frenchName = sorting.findAll("input").find((input) =>
      (input.element as HTMLInputElement).value === "Restes de fruits")!;
    await frenchName.setValue("Restes de fruits et legumes");

    await sorting.find("form").trigger("submit");
    await flushPromises();

    expect(post).not.toHaveBeenCalled();
    const [path, payload] = put.mock.calls[0] as [string, any];
    expect(path).toBe("/admin/sorting-items/7");

    expect(payload.fr.name).toBe("Restes de fruits et legumes");
    // The fields the old form did not know about.
    expect(payload.fr.examples).toEqual(["fruits", "legumes", "pain"]);
    expect(payload.en.examples).toEqual(["fruit", "vegetables", "bread"]);
    expect(payload.zh.examples).toEqual(["水果", "蔬菜", "面包"]);
    expect(payload.fr.availability).toBe("Toute l'annee");
    expect(payload.zh.availability).toBe("全年");
    // And the rest of the record.
    expect(payload.zh.name).toBe("水果残渣");
    expect(payload.keywordsFr).toEqual(["fruits", "legumes"]);
    expect(payload.sourceUrl).toBe("https://blainville.ca/organics");
  });

  it("carries a collection's trilingual note through a date change", async () => {
    const wrapper = await mountAdmin();
    const schedule = panel(wrapper, "Calendrier des collectes");

    await editButton(schedule).trigger("click");
    await flushPromises();
    await schedule.find('input[type="date"]').setValue("2027-03-19");
    await schedule.find("form").trigger("submit");
    await flushPromises();

    const [path, payload] = put.mock.calls[0] as [string, any];
    expect(path).toBe("/admin/collections/42");
    expect(payload.collectionDate).toBe("2027-03-19");
    // Generated calendar rows arrive with this wording on them; a date
    // correction must not strip it.
    expect(payload.noteFr).toBe("Collecte du jeudi.");
    expect(payload.noteZh).toBe("周四收集。");
    expect(payload.sourceUrl).toBe("https://blainville.ca/organics");
  });

  it("goes back to creating after the edit is cancelled", async () => {
    const wrapper = await mountAdmin();
    const sorting = panel(wrapper, "Articles de tri");

    await editButton(sorting).trigger("click");
    await flushPromises();

    const cancel = sorting.findAll("form button").find((button) => button.text() === "Annuler")!;
    await cancel.trigger("click");
    await flushPromises();

    // The form is a create form again, and empty - not still holding the row.
    expect(saveButton(sorting)).toBeUndefined();
    const names = sorting.findAll("input").map((input) => (input.element as HTMLInputElement).value);
    expect(names).not.toContain("Restes de fruits");
  });
});
