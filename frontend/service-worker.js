/**
 * SCADA Mobile — Service Worker (PWA shell).
 *
 * Стратегия: Network-first для API-запросов к бекенду,
 *             Cache-first для статических ресурсов оболочки.
 *
 * API-запросы (к localhost:8080) намеренно НЕ кешируются —
 * данные SCADA должны быть всегда актуальными.
 */

const CACHE_NAME = "scada-mobile-v2";

/** Статические ресурсы оболочки (app shell) */
const SHELL_URLS = [
  "./",
  "./index.html",
  "./css/styles.css",
  "./js/app.js",
  "./js/api.js",
  "./js/ui.js",
  "./manifest.webmanifest",
  "./assets/icons/icon-48x48.png",
  "./assets/icons/icon-96x96.png",
  "./assets/icons/icon-128x128.png",
  "./assets/icons/icon-192x192.png",
  "./assets/icons/icon-512x512.png",
];

// ── Install ──────────────────────────────────────────────────────────
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_URLS))
      .then(() => self.skipWaiting())
      .catch((err) => console.error("[SW] Install failed:", err))
  );
});

// ── Activate ─────────────────────────────────────────────────────────
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((k) => k !== CACHE_NAME)
            .map((k) => caches.delete(k))
        )
      )
      .then(() => self.clients.claim())
  );
});

// ── Fetch ─────────────────────────────────────────────────────────────
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  const isApiRequest = url.pathname.startsWith("/api/");

  // API-запросы к бекенду — только сеть, никакого кеша
  if (isApiRequest) {
    event.respondWith(
      fetch(event.request).catch(() =>
        new Response(
          JSON.stringify({ message: "Network error" }),
          {
            status: 503,
            headers: { "Content-Type": "application/json" },
          }
        )
      )
    );
    return;
  }

  // Для GET-запросов статики — cache-first
  if (event.request.method !== "GET") return;

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;

      return fetch(event.request).then((response) => {
        if (
          response &&
          response.status === 200 &&
          (response.type === "basic" || response.type === "cors")
        ) {
          // Клонируем ДО возврата — body можно прочитать только один раз
          const responseToCache = response.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseToCache);
          });
        }
        return response;
      }).catch(() =>
        new Response("Offline", {
          status: 503,
          statusText: "Offline",
        })
      );
    })
  );
});

// ── Message ───────────────────────────────────────────────────────────
self.addEventListener("message", (event) => {
  if (event.data?.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});
