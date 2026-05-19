# SCADA Mobile — Frontend

Фронтенд-часть проекта находится в этой папке (`frontend/`).

## Purpose

Краткий ориентир по фронтенд-части, запуску и ключевым файлам.

## Table of contents

- [Purpose](#purpose)
- [Технологический стек](#технологический-стек)
- [Структура](#структура)
- [Архитектура](#архитектура)
- [Быстрый запуск](#быстрый-запуск)
- [Сборка](#сборка)
- [Переменные окружения](#переменные-окружения)
- [PWA и Digital Asset Links](#pwa-и-digital-asset-links)

## Технологический стек

- **React 18** + **TypeScript 5** (strict mode)
- **Vite 6** — сборщик и dev-сервер
- **Tailwind CSS 3** — утилитарные стили
- **Zod** — runtime-валидация данных, единый источник типов
- **React Router v7** — маршрутизация с lazy-loading
- **React Admin** — админ-панель для CRUD-операций
- **PWA** — Service Worker, Web Manifest
- **ESLint + Prettier + Husky** — качество кода

> UI/UX спецификация (экраны, поведение, состояния) — в [`UI_UX_SPEC.md`](frontend/UI_UX_SPEC.md).
> Архитектура frontend (компоненты, контексты, data flow) — в [`FRONTEND_ARCHITECTURE.md`](../FRONTEND_ARCHITECTURE.md).
> Описание API и транспортных каналов — в [`api_mapping.md`](../api_mapping.md).
> Целевая архитектура проекта — в [`STRUCTURE.md`](../STRUCTURE.md).

## Структура

```text
frontend/
├── src/
│   ├── admin/             # React Admin панель (ADMIN-only)
│   ├── api/               # REST API клиенты (client.ts, auth.ts, profile.ts, workshops.ts)
│   ├── auth/              # Auth guards (RequireAuth, RequireAdmin), session storage
│   ├── components/        # Переиспользуемые UI-компоненты
│   │   ├── details/       # Вкладки деталей (BatchTab, DevicesTab, QueueTab, LogsTab)
│   │   └── skeleton/      # Skeleton-заглушки для loading-состояний
│   ├── config/            # Константы (runtime.ts, domain.ts, ui.ts, styles.ts)
│   ├── constants/         # Бизнес-логика (statusUtils)
│   ├── context/           # React Context провайдеры
│   │   ├── AuthContext.tsx
│   │   ├── AppContext.tsx
│   │   ├── AccessControlContext.tsx
│   │   ├── DetailsContext.tsx
│   │   └── PageHeaderContext.tsx
│   ├── errors/            # Обработка ошибок (AppError, classifyError, ErrorBoundary)
│   ├── hooks/             # Кастомные React hooks
│   │   ├── useLiveWs.ts       # Глобальный WebSocket /ws/live
│   │   ├── useUnitWs.ts       # Per-unit WebSocket /ws/unit/{unitId}
│   │   ├── useAsyncFetch.ts   # Generic async fetch
│   │   ├── useHeaderErrorSlot.ts
│   │   ├── usePageError.ts
│   │   └── useHardwareBackGuard.ts
│   ├── layouts/           # Layout-компоненты
│   │   ├── RootLayout.tsx
│   │   └── DetailsLayout.tsx
│   ├── lib/               # Утилиты
│   │   ├── createManagedWs.ts    # Фабрика WebSocket с reconnect
│   │   ├── notificationSwBridge.ts
│   │   └── viewport.ts
│   ├── pages/             # Страницы (DashboardPage, WorkshopPage, LoginPage, ProfilePage, NotificationsPage)
│   ├── schemas/           # Zod-схемы (topology.ts, ws.ts, profile.ts, env.ts)
│   ├── types/             # TypeScript типы (inferred from Zod + UI types)
│   ├── App.tsx            # Корневой компонент
│   ├── main.tsx           # Точка входа
│   └── router.tsx         # Конфигурация React Router
├── public/
│   ├── manifest.webmanifest   # Web App Manifest
│   ├── service-worker.js      # Service Worker (offline cache)
│   ├── well-known/
│   │   └── assetlinks.json    # Digital Asset Links (TWA)
│   └── assets/
│       ├── icons/             # Иконки приложения
│       └── screenshots/       # Скриншоты для сторов
├── index.html
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.app.json
├── eslint.config.js
├── prettier.config.js
└── deploy/
    └── nginx.conf         # Шаблон конфигурации Nginx
```

## Архитектура

### Двухуровневая модель данных

1. **Статическая топология** — загружается через REST (цехи, автоматы, устройства), кешируется через ETag (`If-None-Match` → 304).
2. **Live-статусы** — приходят через WebSocket в реальном времени (алерты, статусы автоматов, уведомления).

### Управление состоянием

Без внешних state-библиотек (Redux/Zustand). Используются React Context:

- **AuthContext** — аутентификация (userId, role, isAdmin, login/logout).
- **AppContext** — центральное состояние приложения (алерты, уведомления, топология, статусы, ошибки).
- **AccessControlContext** — назначения пользователя на автоматы, права доступа.
- **DetailsContext** — данные для вкладок деталей автомата (line, devices, queue, errors).
- **PageHeaderContext** — декларативная конфигурация шапки страницы.

### Валидация данных

Все внешние данные (REST-ответы, WebSocket-сообщения) валидируются через Zod. TypeScript-типы выводятся из Zod-схем через `z.infer<>`. Это гарантирует соответствие runtime-данных и compile-time типов.

### WebSocket

- **Глобальный канал** `/ws/live` — единое соединение на всю сессию, мультиплексирование сообщений.
- **Per-unit канал** `/ws/unit/{unitId}` — отдельное соединение для деталей автомата.
- **Reconnect** — экспоненциальный backoff + jitter через `createManagedWs`.

### Аутентификация

- Логин по `workerCode` + `password`.
- Access token в `localStorage`, автоматическая подстановка в заголовок `Authorization: Bearer`.
- При 401 — автоматический refresh через refresh token, повтор запроса.
- При неудаче refresh — редирект на `/login`.

### Админ-панель

- React Admin под `/admin/*`.
- Кастомный `dataProvider` адаптирует Spring Boot API к формату React Admin.
- Доступ только для `ADMIN` (защита через `RequireAdmin`).

## Быстрый запуск

```bash
cd frontend
npm install
npm run dev
```

Откройте в браузере: `http://localhost:5500/`

> Порт **5500** включён в CORS-allowlist dev-профиля бэкенда.

## Сборка

```bash
npm run build      # TypeScript + Vite production build
npm run type-check # Только проверка типов
npm run lint       # ESLint
npm run format     # Prettier
```

## Переменные окружения

Создайте `.env.local` при необходимости переопределить эндпоинты:

```env
VITE_API_BASE=http://localhost:8080
VITE_WS_BASE=ws://localhost:8080
```

## PWA и Digital Asset Links

- `public/manifest.webmanifest` — метаданные приложения, `start_url`, иконки
- `public/service-worker.js` — стратегия cache-first для offline-режима
- `public/well-known/assetlinks.json` — связывает веб-сайт с Android-приложением (убирает адресную строку в TWA)

> Настройка Digital Asset Links и сборка Android-приложения — в [`android/README.md`](../android/README.md).
