import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { api, ApiError } from "./client";
import { useAuthStore } from "../stores/auth";

/**
 * The shared request path. Everything the app asks the backend goes through
 * here, so what it does with a failure matters more than what it does with a
 * success.
 */
describe("api client", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  function respondWith(init: { status: number; body?: unknown; headers?: Record<string, string> }) {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(init.body === undefined ? null : JSON.stringify(init.body), {
        status: init.status,
        headers: { "Content-Type": "application/json", ...(init.headers ?? {}) }
      })
    );
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("sends the bearer token when there is a session, and no header when there is not", async () => {
    const fetchMock = respondWith({ status: 200, body: { ok: true } });

    await api.get("/anything");
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("Authorization")).toBeNull();

    useAuthStore().setSession({ token: "t0ken", userId: 1, email: "a@b.c", role: "ADMIN" });
    await api.get("/anything");
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get("Authorization")).toBe("Bearer t0ken");
  });

  it("logs out on a 401 so an expired token cannot linger in the browser", async () => {
    const auth = useAuthStore();
    auth.setSession({ token: "expired", userId: 1, email: "a@b.c", role: "USER" });
    respondWith({ status: 401, body: { message: "Authentication required" } });

    await expect(api.get("/preferences")).rejects.toBeInstanceOf(ApiError);

    expect(auth.isAuthenticated).toBe(false);
    expect(localStorage.getItem("bienvenue-blainville.auth")).toBeNull();
  });

  it("does not log out on other failures", async () => {
    const auth = useAuthStore();
    auth.setSession({ token: "valid", userId: 1, email: "a@b.c", role: "USER" });
    respondWith({ status: 403, body: { message: "Forbidden" } });

    await expect(api.get("/admin/notices")).rejects.toThrow("Forbidden");

    expect(auth.isAuthenticated).toBe(true);
  });

  it("carries the server's message and status on the error", async () => {
    respondWith({ status: 422, body: { message: "endsOn must not be before startsOn" } });

    await expect(api.post("/admin/notices", {})).rejects.toMatchObject({
      status: 422,
      message: "endsOn must not be before startsOn"
    });
  });

  it("reads Retry-After, and reports null when the server did not send one", async () => {
    respondWith({ status: 503, body: { message: "still preparing" }, headers: { "Retry-After": "2" } });
    await expect(api.post("/photos/x/identify")).rejects.toMatchObject({ retryAfter: 2 });

    // The same status without the header means something retrying cannot fix.
    respondWith({ status: 503, body: { message: "needs an API key" } });
    await expect(api.post("/photos/x/identify")).rejects.toMatchObject({ retryAfter: null });
  });

  it("returns nothing for 204, rather than trying to parse an empty body", async () => {
    respondWith({ status: 204 });

    await expect(api.delete("/admin/notices/1")).resolves.toBeUndefined();
  });
});
