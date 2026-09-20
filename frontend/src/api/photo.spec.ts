import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { askAboutPhoto } from "./photo";

/**
 * The upload-then-ask flow, and the gap in the middle.
 *
 * S3 triggers the processing Lambda asynchronously, so there is a window where
 * the photo is uploaded and the answer is not ready. The server says so with a
 * 503 carrying Retry-After. The same endpoint has a second 503 — no API key is
 * configured — which no amount of retrying will fix, so the header, not the
 * status, is what decides whether to try again.
 */
describe("askAboutPhoto", () => {
  const file = new File([new Uint8Array([1, 2, 3])], "bin.jpg", { type: "image/jpeg" });
  const answer = { identifiedAs: "carton", grounded: true, answer: "Bac bleu.", provider: "vision", sources: [] };

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.useFakeTimers();
  });

  /** Ticket, then the S3 PUT, then however many identify responses are given. */
  function backend(...identifyResponses: Response[]) {
    const calls: string[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      void init;
      const url = String(input);
      calls.push(url);

      if (url.endsWith("/photos/upload-url")) {
        return jsonResponse(200, { photoId: "photo-1", uploadUrl: "https://s3.example/put", expiresIn: 300 });
      }
      if (url.startsWith("https://s3.example")) {
        return new Response(null, { status: 200 });
      }
      return identifyResponses.shift() ?? jsonResponse(500, { message: "no response left" });
    });

    vi.stubGlobal("fetch", fetchMock);
    return { calls, fetchMock };
  }

  const jsonResponse = (status: number, body: unknown, headers: Record<string, string> = {}) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json", ...headers }
    });

  const stillPreparing = () =>
    jsonResponse(503, { message: "That photo is still being prepared." }, { "Retry-After": "2" });

  it("uploads to storage directly, without the app's Authorization header", async () => {
    const { fetchMock } = backend(jsonResponse(200, answer));
    localStorage.setItem(
      "bienvenue-blainville.auth",
      JSON.stringify({ token: "resident-token", userId: 1, email: "a@b.c", role: "USER" })
    );
    setActivePinia(createPinia());

    const result = askAboutPhoto(file, "fr");
    await vi.runAllTimersAsync();
    await result;

    const upload = fetchMock.mock.calls.find(([url]) => String(url).startsWith("https://s3.example"));
    expect(upload).toBeDefined();
    // Sending a resident's JWT to a storage provider would be a credential
    // leak with nothing gained.
    expect(new Headers(upload![1]?.headers).get("Authorization")).toBeNull();
  });

  it("waits and asks again while the server says the photo is still being prepared", async () => {
    const { calls } = backend(stillPreparing(), stillPreparing(), jsonResponse(200, answer));

    const result = askAboutPhoto(file, "fr");
    await vi.runAllTimersAsync();

    await expect(result).resolves.toMatchObject({ photoId: "photo-1", answer });
    expect(calls.filter((url) => url.includes("/identify"))).toHaveLength(3);
  });

  it("does not retry a 503 that carries no Retry-After", async () => {
    // "Photo identification needs an Anthropic API key" is also a 503, and
    // retrying it just hammers a misconfiguration.
    const { calls } = backend(jsonResponse(503, { message: "needs an Anthropic API key" }));

    const rejected = expect(askAboutPhoto(file, "fr")).rejects.toThrow("needs an Anthropic API key");
    await vi.runAllTimersAsync();
    await rejected;

    expect(calls.filter((url) => url.includes("/identify"))).toHaveLength(1);
  });

  it("gives up rather than retrying for ever", async () => {
    const { calls } = backend(stillPreparing(), stillPreparing(), stillPreparing(), stillPreparing(), stillPreparing());

    const rejected = expect(askAboutPhoto(file, "fr")).rejects.toThrow("still being prepared");
    await vi.runAllTimersAsync();
    await rejected;

    expect(calls.filter((url) => url.includes("/identify")).length).toBeLessThanOrEqual(4);
  });

  it("reports the upload failing rather than asking about a photo that is not there", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        void init;
        const url = String(input);
        if (url.endsWith("/photos/upload-url")) {
          return jsonResponse(200, { photoId: "photo-1", uploadUrl: "https://s3.example/put", expiresIn: 300 });
        }
        if (url.startsWith("https://s3.example")) {
          return new Response(null, { status: 403 });
        }
        throw new Error("identify must not be called when the upload failed");
      })
    );

    const rejected = expect(askAboutPhoto(file, "fr")).rejects.toThrow("Upload failed with status 403");
    await vi.runAllTimersAsync();
    await rejected;
  });
});
