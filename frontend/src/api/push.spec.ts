import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  browserSupportsPush,
  decodeApplicationServerKey,
  subscribeToPush,
  unsubscribeFromPush
} from "./push";

const post = vi.fn();
const del = vi.fn();

vi.mock("./client", () => ({
  api: {
    get: vi.fn(),
    post: (...args: unknown[]) => post(...args),
    delete: (...args: unknown[]) => del(...args)
  }
}));

/**
 * The page's side of push.
 *
 * The ordering test is the one worth having: unsubscribing in the browser
 * first and then telling the server leaves a row pointing at an endpoint
 * nobody will ever answer, and every future notice pays for a delivery that
 * cannot succeed.
 */
describe("push subscriptions", () => {
  const keyBytes = (fill: number, length: number) => {
    const bytes = new Uint8Array(length);
    bytes.fill(fill);
    return bytes.buffer;
  };

  beforeEach(() => {
    post.mockReset();
    del.mockReset();
    post.mockResolvedValue(undefined);
    del.mockResolvedValue(undefined);
  });

  it("decodes a base64url application server key the Push API will accept", () => {
    // 65 bytes, because that is an uncompressed P-256 point, and the length is
    // the part a browser rejects loudly.
    const key = "BJX1f4oKaluqD9KfY868_KZyA0nNqCFao5xWFDuudr-N5QKNf61tFaOoHIRLI3uKRDTeAoHVfmhBhpW1hNadOb4";

    const decoded = decodeApplicationServerKey(key);

    expect(decoded).toBeInstanceOf(Uint8Array);
    expect(decoded.length).toBe(65);
    expect(decoded[0]).toBe(0x04);
  });

  it("reports no support when the browser has no PushManager", () => {
    expect(browserSupportsPush()).toBe(false);
  });

  it("sends the endpoint and both keys when the resident allows it", async () => {
    const subscription = {
      endpoint: "https://push.example/wpush/v2/abc",
      getKey: (name: string) => (name === "p256dh" ? keyBytes(1, 65) : keyBytes(2, 16))
    };
    vi.stubGlobal("Notification", { requestPermission: vi.fn().mockResolvedValue("granted") });
    vi.stubGlobal("navigator", {
      serviceWorker: {
        register: vi.fn().mockResolvedValue({
          pushManager: { subscribe: vi.fn().mockResolvedValue(subscription) }
        })
      }
    });

    await subscribeToPush("BJX1f4oKaluqD9KfY868_KZyA0nNqCFao5xWFDuudr-N5QKNf61tFaOoHIRLI3uKRDTeAoHVfmhBhpW1hNadOb4", "zh");

    expect(post).toHaveBeenCalledWith("/push/subscriptions", expect.objectContaining({
      endpoint: "https://push.example/wpush/v2/abc",
      languageCode: "zh"
    }));
    expect(post.mock.calls[0][1].p256dh).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it("does not register anything when the resident refuses", async () => {
    vi.stubGlobal("Notification", { requestPermission: vi.fn().mockResolvedValue("denied") });
    const subscribe = vi.fn();
    vi.stubGlobal("navigator", {
      serviceWorker: {
        register: vi.fn().mockResolvedValue({ pushManager: { subscribe } })
      }
    });

    await expect(subscribeToPush("BJX1", "fr")).rejects.toThrow("notification-permission-denied");
    expect(subscribe).not.toHaveBeenCalled();
    expect(post).not.toHaveBeenCalled();
  });

  it("tells the server before the browser forgets the endpoint", async () => {
    const order: string[] = [];
    const unsubscribe = vi.fn().mockImplementation(async () => {
      order.push("browser");
      return true;
    });
    del.mockImplementation(async () => {
      order.push("server");
    });
    // browserSupportsPush checks for Notification *on window*. Leaving it off
    // made both of these tests pass by taking the early return instead of the
    // path they are about.
    vi.stubGlobal("window", { PushManager: class {}, Notification: {} });
    vi.stubGlobal("Notification", {});
    vi.stubGlobal("navigator", {
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue({
          pushManager: {
            getSubscription: vi.fn().mockResolvedValue({
              endpoint: "https://push.example/wpush/v2/abc",
              unsubscribe
            })
          }
        })
      }
    });

    await unsubscribeFromPush();

    expect(order).toEqual(["server", "browser"]);
    expect(del).toHaveBeenCalledWith(
      "/push/subscriptions?endpoint=https%3A%2F%2Fpush.example%2Fwpush%2Fv2%2Fabc");
  });

  it("does nothing when there is no subscription to remove", async () => {
    // browserSupportsPush checks for Notification *on window*. Leaving it off
    // made both of these tests pass by taking the early return instead of the
    // path they are about.
    vi.stubGlobal("window", { PushManager: class {}, Notification: {} });
    vi.stubGlobal("Notification", {});
    vi.stubGlobal("navigator", {
      serviceWorker: { getRegistration: vi.fn().mockResolvedValue(undefined) }
    });

    await unsubscribeFromPush();

    expect(del).not.toHaveBeenCalled();
  });
});
