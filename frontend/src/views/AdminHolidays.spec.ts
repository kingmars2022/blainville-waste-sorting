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
 * Administering closed days from the console.
 *
 * The coupling worth a test is the one that is easy to leave out: saving a
 * holiday changes the schedule, because the server moves every collection off
 * the closed day. A panel that reloaded only its own list would leave the
 * schedule table above it showing collections on a day the city is shut, and
 * the administrator would have no reason to suspect it.
 */
/** The holidays panel is the last of its kind on the page. `at(-1)` would say
 *  this more directly, but it is newer than the lib target this project
 *  compiles against, and a test is not a reason to move a build target. */
function lastOf<T>(items: T[]): T {
  const item = items[items.length - 1];
  if (item === undefined) {
    throw new Error("expected at least one element");
  }
  return item;
}

describe("AdminView holidays", () => {
  const holiday = {
    id: 3,
    holidayDate: "2027-01-01",
    nameFr: "Jour de l'An",
    nameEn: "New Year's Day",
    nameZh: "元旦",
    shiftDays: 1,
    sector: "all",
    sourceUrl: null,
    active: true
  };

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    get.mockImplementation(async (path: string) => {
      if (path === "/admin/collection-holidays") return [holiday];
      if (path.startsWith("/admin/collections")) {
        return { items: [], page: 0, size: 25, total: 0 };
      }
      return [];
    });
    post.mockResolvedValue({ ...holiday, id: 9 });
    put.mockResolvedValue(holiday);
    del.mockResolvedValue(undefined);
  });

  async function mountAdmin() {
    const wrapper = mount(AdminView);
    await flushPromises();
    return wrapper;
  }

  it("lists the closed days in the console's own language", async () => {
    const wrapper = await mountAdmin();

    expect(get).toHaveBeenCalledWith("/admin/collection-holidays");
    expect(wrapper.text()).toContain("2027-01-01");
    // The console defaults to French, so the French name is the one shown -
    // the same per-language pick a resident gets on a moved collection.
    expect(wrapper.text()).toContain("Jour de l'An");
    expect(wrapper.text()).not.toContain("New Year's Day");
  });

  it("sends every language, because a moved collection explains itself in all three", async () => {
    const wrapper = await mountAdmin();

    const form = lastOf(wrapper.findAll("form"));
    const inputs = form.findAll("input");
    await inputs[0].setValue("2027-07-01");
    const [fr, en, zh] = form.findAll('input[type="text"]');
    await fr.setValue("Fete du Canada");
    await en.setValue("Canada Day");
    await zh.setValue("加拿大国庆日");
    await form.trigger("submit");
    await flushPromises();

    expect(post).toHaveBeenCalledWith(
      "/admin/collection-holidays",
      expect.objectContaining({
        holidayDate: "2027-07-01",
        nameFr: "Fete du Canada",
        nameEn: "Canada Day",
        nameZh: "加拿大国庆日",
        shiftDays: 1,
        sector: "all"
      })
    );
  });

  it("reloads the schedule after saving, because the server just moved collections", async () => {
    const wrapper = await mountAdmin();
    get.mockClear();

    const form = lastOf(wrapper.findAll("form"));
    await form.findAll("input")[0].setValue("2027-07-01");
    const [fr, en, zh] = form.findAll('input[type="text"]');
    await fr.setValue("a");
    await en.setValue("b");
    await zh.setValue("c");
    await form.trigger("submit");
    await flushPromises();

    const reloaded = get.mock.calls.map((call) => String(call[0]));
    expect(reloaded).toContain("/admin/collection-holidays");
    expect(reloaded.some((path) => path.startsWith("/admin/collections"))).toBe(true);
  });

  it("reloads the schedule after deleting one too", async () => {
    const wrapper = await mountAdmin();
    get.mockClear();

    const deleteButton = lastOf(
      wrapper.findAll("button").filter((button) => button.text() === "Supprimer")
    );
    await deleteButton.trigger("click");
    await flushPromises();

    expect(del).toHaveBeenCalledWith("/admin/collection-holidays/3");
    const reloaded = get.mock.calls.map((call) => String(call[0]));
    expect(reloaded.some((path) => path.startsWith("/admin/collections"))).toBe(true);
  });

  it("shows the server's reason when a holiday is refused", async () => {
    const wrapper = await mountAdmin();
    post.mockRejectedValueOnce(new Error("shiftDays must be between 1 and 7"));

    const form = lastOf(wrapper.findAll("form"));
    await form.findAll("input")[0].setValue("2027-07-01");
    const [fr, en, zh] = form.findAll('input[type="text"]');
    await fr.setValue("a");
    await en.setValue("b");
    await zh.setValue("c");
    await form.trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("shiftDays must be between 1 and 7");
  });
});
