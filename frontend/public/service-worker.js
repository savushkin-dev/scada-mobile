/**
 * SCADA Mobile — Service Worker (PWA shell).
 *
 * Стратегия: Network-first для API-запросов к бекенду,
 *             Cache-first для статических ресурсов оболочки.
 *
 * API-запросы (к localhost:8080) намеренно НЕ кешируются —
 * данные SCADA должны быть всегда актуальными.
 */

const CACHE_PREFIX = 'scada-mobile-';
const SW_VERSION = new URL(self.location.href).searchParams.get('v') ?? 'dev';
const CACHE_NAME = `${CACHE_PREFIX}${SW_VERSION}`;

const NOTIFICATION_PREFIX = 'manual-call:';
const RESHOW_COOLDOWN_MS = 2500;
const activeNotifications = new Map();
const lastReshowAt = new Map();

/** Статические ресурсы оболочки (app shell).
 *  Vite генерирует JS/CSS с хешами в именах — они кешируются динамически
 *  стратегией cache-first в обработчике fetch.
 *  Здесь перечислены только стабильные URL без хешей.
 */
const SHELL_URLS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './assets/icons/icon-48x48.png',
  './assets/icons/icon-96x96.png',
  './assets/icons/icon-128x128.png',
  './assets/icons/icon-192x192.png',
  './assets/icons/icon-512x512.png',
];

// ── Install ──────────────────────────────────────────────────────────
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_URLS))
      .then(() => self.skipWaiting())
      .catch((err) => console.error('[SW] Install failed:', err))
  );
});

// ── Activate ─────────────────────────────────────────────────────────
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((k) => k.startsWith(CACHE_PREFIX) && k !== CACHE_NAME)
            .map((k) => caches.delete(k))
        )
      )
      .then(() => self.clients.claim())
  );
});

// ── Fetch ─────────────────────────────────────────────────────────────
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  const isApiRequest = url.pathname.startsWith('/api/');

  // API-запросы к бекенду — только сеть, никакого кеша
  if (isApiRequest) {
    event.respondWith(
      fetch(event.request).catch(
        () =>
          new Response(JSON.stringify({ message: 'Network error' }), {
            status: 503,
            headers: { 'Content-Type': 'application/json' },
          })
      )
    );
    return;
  }

  // Для GET-запросов статики — cache-first
  if (event.request.method !== 'GET') return;

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;

      return fetch(event.request)
        .then((response) => {
          if (
            response &&
            response.status === 200 &&
            (response.type === 'basic' || response.type === 'cors')
          ) {
            // Клонируем ДО возврата — body можно прочитать только один раз
            const responseToCache = response.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseToCache);
            });
          }
          return response;
        })
        .catch(
          () =>
            new Response('Offline', {
              status: 503,
              statusText: 'Offline',
            })
        );
    })
  );
});

// ── Message ───────────────────────────────────────────────────────────
self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }

  if (event.data?.type === 'NOTIFICATION_EVENT') {
    const payload = event.data.payload;
    if (!payload || !payload.unitId) return;

    if (payload.active) {
      activeNotifications.set(payload.unitId, payload);
      event.waitUntil(showManualNotification(payload));
    } else {
      activeNotifications.delete(payload.unitId);
      event.waitUntil(closeManualNotification(payload.unitId));
    }
  }

  if (event.data?.type === 'NOTIFICATION_SNAPSHOT') {
    const payload = Array.isArray(event.data.payload) ? event.data.payload : [];
    const nextIds = new Set();

    for (const item of payload) {
      if (!item || !item.unitId || !item.active) continue;
      activeNotifications.set(item.unitId, item);
      nextIds.add(item.unitId);
      event.waitUntil(showManualNotification(item));
    }

    for (const unitId of activeNotifications.keys()) {
      if (!nextIds.has(unitId)) {
        activeNotifications.delete(unitId);
        event.waitUntil(closeManualNotification(unitId));
      }
    }
  }
});

// ── Notifications ─────────────────────────────────────────────────────

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      for (const client of clients) {
        if ('focus' in client) return client.focus();
      }
      return self.clients.openWindow('/');
    })
  );
});

self.addEventListener('notificationclose', (event) => {
  const unitId = event.notification?.data?.unitId;
  if (!unitId) return;
  if (!activeNotifications.has(unitId)) return;

  const now = Date.now();
  const lastAt = lastReshowAt.get(unitId) ?? 0;
  if (now - lastAt < RESHOW_COOLDOWN_MS) return;

  lastReshowAt.set(unitId, now);
  const payload = activeNotifications.get(unitId);
  if (!payload) return;

  event.waitUntil(showManualNotification(payload));
});

function notificationTag(unitId) {
  return `${NOTIFICATION_PREFIX}${unitId}`;
}

function buildNotificationTitle(payload) {
  const unitName = payload.unitName || payload.unitId;
  return `Последняя партия: ${unitName}`;
}

function buildNotificationBody(payload) {
  const creator = payload.creatorId ? `Отправитель: ${payload.creatorId}` : 'Отправитель: —';
  const timestamp = payload.timestamp ? `Время: ${payload.timestamp}` : null;
  return [creator, timestamp].filter(Boolean).join('\n');
}

function showManualNotification(payload) {
  const unitId = payload.unitId;
  if (!unitId) return Promise.resolve();

  return self.registration.showNotification(buildNotificationTitle(payload), {
    body: buildNotificationBody(payload),
    tag: notificationTag(unitId),
    renotify: true,
    requireInteraction: true,
    icon: '/assets/icons/icon-192x192.png',
    badge: '/assets/icons/icon-96x96.png',
    data: {
      unitId,
    },
  });
}

function closeManualNotification(unitId) {
  return self.registration
    .getNotifications({ tag: notificationTag(unitId) })
    .then((notifications) => {
      notifications.forEach((notification) => notification.close());
    });
}
