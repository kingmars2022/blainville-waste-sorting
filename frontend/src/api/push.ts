import { api } from "./client";

/**
 * Browser push, from the page's side.
 *
 * <p>Three things have to line up before a subscription exists: the browser
 * supports service workers and push, the resident grants permission, and the
 * server has a VAPID key. Each is checked separately because each fails
 * differently and only one of them is the resident's choice.
 */
export type PushAvailability = {
  enabled: boolean;
  publicKey: string;
};

export function browserSupportsPush(): boolean {
  return typeof navigator !== "undefined"
    && "serviceWorker" in navigator
    && typeof window !== "undefined"
    && "PushManager" in window
    && "Notification" in window;
}

export async function serverPushSettings(): Promise<PushAvailability> {
  return api.get<PushAvailability>("/push/public-key");
}

/** base64url to the Uint8Array the Push API insists on. */
export function decodeApplicationServerKey(base64Url: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  // An explicit ArrayBuffer, not a plain Uint8Array: applicationServerKey is
  // typed as BufferSource, which a SharedArrayBuffer-backed view does not
  // satisfy.
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}

function keyToBase64Url(key: ArrayBuffer | null): string {
  if (!key) {
    throw new Error("The browser returned a subscription without keys");
  }
  const bytes = new Uint8Array(key);
  let binary = "";
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!browserSupportsPush()) {
    return null;
  }
  const registration = await navigator.serviceWorker.getRegistration();
  if (!registration) {
    return null;
  }
  return registration.pushManager.getSubscription();
}

export async function subscribeToPush(publicKey: string, languageCode: string): Promise<void> {
  const registration = await navigator.serviceWorker.register("/sw.js");
  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    // Denied is a decision, not an error to retry. Saying so lets the caller
    // show why the toggle did not stay on.
    throw new Error("notification-permission-denied");
  }

  const subscription = await registration.pushManager.subscribe({
    // Required by every browser: a push service will not deliver to a
    // subscription that any site could read.
    userVisibleOnly: true,
    applicationServerKey: decodeApplicationServerKey(publicKey)
  });

  await api.post("/push/subscriptions", {
    endpoint: subscription.endpoint,
    p256dh: keyToBase64Url(subscription.getKey("p256dh")),
    auth: keyToBase64Url(subscription.getKey("auth")),
    languageCode
  });
}

export async function unsubscribeFromPush(): Promise<void> {
  const subscription = await currentSubscription();
  if (!subscription) {
    return;
  }
  // Tell the server first. If the browser unsubscribes and the call then
  // fails, the row is left pointing at an endpoint nobody will ever answer.
  await api.delete(`/push/subscriptions?endpoint=${encodeURIComponent(subscription.endpoint)}`);
  await subscription.unsubscribe();
}
