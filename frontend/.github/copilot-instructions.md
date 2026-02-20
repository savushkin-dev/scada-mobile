# Инструкция для нейроагента — Frontend (SCADA Mobile)

Эта инструкция читается **при каждой задаче** в папке `frontend/`. Здесь описано всё, что нужно знать агенту, чтобы сразу писать правильный код.

---

## 1. Что это за приложение

**Информационный диспетчер** для сотрудников молочного предприятия. Отображает текущее состояние аппаратов и участков производственных цехов в режиме реального времени.

Главная задача UI — **быстро и понятно показать**: какой аппарат стоит, с каким статусом и сообщением. Целевая аудитория — рабочие и операторы в цехе, часто со смартфона, в условиях плохой сети.

**Принцип UI:** простота и скорость. Информация должна читаться с первого взгляда. Никаких сложных форм, dashboards с десятками виджетов, модальных цепочек.

---

## 2. Текущее состояние → целевое

| | Сейчас | Целевое |
|---|---|---|
| Технологии | Vanilla HTML + CSS + JS (ES-модули) | **React 18 + TypeScript 5 + Vite** |
| Стейт/данные | ручная кнопка + fetch | **React Query** (polling или WebSocket в будущем) |
| Валидация данных | JSDoc-типы | **Zod** (runtime-валидация ответов API) |
| Линтинг | нет | ESLint + Prettier |

Текущие файлы в `frontend/` — **рабочий прототип**. При переписывании на React + Vite — держать ту же структуру обязательных файлов и ту же логику API (см. ниже).

---

## 3. Целевая структура (React + Vite)

```text
frontend/
├── src/
│   ├── components/       # UI-компоненты (UnitCard, StatusBadge и т.д.)
│   ├── pages/            # Страницы (если появится роутинг)
│   ├── hooks/            # кастомные хуки (useSnapshot, useSetValue)
│   └── api/              # API-клиент + типы (в т.ч. сгенерированные из OpenAPI)
├── public/               # статика, копируется as-is в dist
│   └── .well-known/
│       └── assetlinks.json  # ⚠️ ОБЯЗАТЕЛЕН для TWA (Android)
├── .well-known/          # текущее расположение в прототипе
│   └── assetlinks.json
├── assets/
│   ├── icons/            # иконки 48/96/128/192/512px (PNG)
│   └── screenshots/      # скриншоты (desktop/mobile) для манифеста
├── manifest.webmanifest  # ⚠️ ОБЯЗАТЕЛЕН для PWA
├── service-worker.js     # ⚠️ ОБЯЗАТЕЛЕН для PWA
├── index.html            # точка входа (для Vite — здесь <div id="root">)
├── vite.config.ts
├── tsconfig.json         # strict: true
├── eslint.config.js
└── prettier.config.js
```

---

## 4. Обязательные файлы PWA/TWA — нельзя удалять или переименовывать

| Файл | Зачем |
|---|---|
| `manifest.webmanifest` | PWA: установка на экран, иконки, `start_url`, `scope` |
| `service-worker.js` | PWA: offline-режим; кэш-шела приложения |
| `.well-known/assetlinks.json` | TWA: убирает адресную строку Chrome в Android-приложении |
| `assets/icons/icon-192x192.png` | PWA: обязательный размер иконки |
| `assets/icons/icon-512x512.png` | PWA: обязательный размер иконки |

Эти файлы должны быть **доступны по прямому URL** в production (не под хешами Vite). В `vite.config.ts` — размещать их в `public/` или прописывать явный `rollupOptions.input`.

**Netlify:** файл `netlify.toml` содержит редирект `/.well-known/:splat → /well-known/:splat` — сохранять его.

---

## 5. Доменная модель (данные от бекенда)

Бекенд отдаёт **snapshot** — снимок состояния всех units (аппаратов) в момент последнего опроса PrintSrv.

```typescript
interface UnitState {
  State:      string | null;   // статус аппарата (работает / стоит / ждёт)
  Task:       string | null;   // текущее задание
  Counter:    number | null;   // счётчик
  Properties: {
    command:      number | null;
    message:      string | null;
    Error:        string | null;
    ErrorMessage: string | null;
  } | null;
}

interface QueryStateResponse {
  DeviceName: string;               // например "Line"
  Units: Record<string, UnitState>; // ключи: "u1", "u2", ...
}
```

**Ключевое для UI:**
- Данные **eventual consistent**: после отправки команды новое состояние появится только в следующем snapshot (~5 сек).
- API **не гарантирует** мгновенного применения — показывай пользователю «команда принята», а не «команда выполнена».
- Если `Error` или `ErrorMessage` не `null` — аппарат в ошибке, это нужно визуально выделить.

---

## 6. API-интеграция

### Эндпоинты

| | Метод | URL | Назначение |
|---|---|---|---|
| Snapshot | GET | `/api/v1/commands/queryAll` | Получить все units |
| Команда | POST | `/api/v1/commands/setUnitVars?unit={n}&value={v}` | Отправить команду на аппарат `n` (1-based) |
| Health | GET | `/api/v1/commands/health/ready` | Проверка доступности бекенда |

### Определение базового URL (из прототипа)

```typescript
// index.html (inline script) — до загрузки бандла:
const port = location.port;
if (port !== "" && port !== "80" && port !== "443") {
  window.SCADA_API_BASE_URL = `${location.protocol}//${location.hostname}:8080`;
}
// В Vite-приложении лучше использовать VITE_API_BASE_URL из .env
```

### Паттерн API-слоя (сохранять в React-версии)

Весь код, знающий об URL и структуре ответов, — **только в `src/api/`**. Компоненты и хуки напрямую с `fetch` не работают.

Ошибки бекенда приходят в формате **Problem+JSON (RFC 7807)** — парсить `response.json()` при не-2xx статусе.

---

## 7. Service Worker — стратегия кэширования

Сохранять логику при любом рефакторинге:

- **Статика приложения (app shell)** → Cache-first.
- **API-запросы** (`/api/*`) → **Network-only, без кэша**. Данные SCADA должны быть всегда актуальными.

---

## 8. TypeScript — правила

- `strict: true` в `tsconfig.json`.
- Запрет `any` — используй `unknown` + type guards или Zod.
- Все типы ответов API — либо автогенерируются из OpenAPI (`npm run gen:api`), либо вручную в `src/api/types.ts`.
- При переходе на Vite: переменные окружения — только через `import.meta.env.VITE_*`.

---

## 9. Команды разработки

```bash
make front-install   # установка зависимостей (npm ci)
make front-dev       # dev-сервер (Vite, порт 5173)
make front-build     # production-сборка (dist/)
```

Для прототипа (vanilla) — любой статический сервер на порту 5500:
```bash
cd frontend && python -m http.server 5500
```
Порт 5500 включён в CORS allowlist dev-профиля бекенда.

---

## 10. Что нельзя делать в фронтенде

- **Не хранить** секреты, токены, пароли в коде, `.env`-файлах в репозитории или `localStorage`.
- **Не дублировать** бизнес-логику из бекенда (валидация диапазонов unit/value — на бекенде).
- **Не трогать** `assetlinks.json` без синхронизации с командой Android/бекенда — изменение SHA-256 отпечатка ломает TWA.
- **Не кэшировать** API-ответы в Service Worker.
- **Не полагаться** на то, что команда применилась мгновенно — всегда есть задержка scan cycle (~5 сек).
