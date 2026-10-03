/*
 * Service worker: the only part of this application that runs when the page
 * does not.
 *
 * It is deliberately small. Everything it needs arrives in the push payload,
 * so it never calls the API - a worker woken at 6am to show a notice should
 * not depend on the backend being reachable, and it has no session to call
 * with anyway.
 */

self.addEventListener("push", (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    // A payload this worker cannot parse is still a reason to tell the
    // resident something arrived, rather than silently dropping it.
    payload = {};
  }

  const title = payload.title || "Blainville";
  const options = {
    body: payload.body || "",
    // Collapses repeats of the same notice rather than stacking them, which is
    // what an at-least-once delivery pipeline will eventually produce.
    tag: payload.noticeId ? `notice-${payload.noticeId}` : "notice",
    data: { noticeId: payload.noticeId || null },
    icon: "/favicon.ico",
  };

  // waitUntil, or the worker can be killed before the notification is shown.
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      // Focus an open tab rather than opening a second one.
      for (const client of windows) {
        if ("focus" in client) {
          return client.focus();
        }
      }
      return self.clients.openWindow("/");
    })
  );
});
