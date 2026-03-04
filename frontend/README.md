# SCADA Mobile — Frontend

Фронтенд-часть проекта находится в этой папке (`frontend/`).

## Технологический стек

- **React 18** + **TypeScript 5** (strict mode)
- **Vite 6** — сборщик и dev-сервер
- **Tailwind CSS 3** — утилитарные стили
- **PWA** — Service Worker, Web Manifest
- **ESLint + Prettier** — качество кода

> UI/UX спецификация (экраны, поведение, состояния) — в [`UI_UX_SPEC.md`](UI_UX_SPEC.md).
> Описание API и транспортных каналов — в [`api_mapping.md`](../api_mapping.md).
> Целевая архитектура проекта — в [`STRUCTURE.md`](../STRUCTURE.md).

## Структура

```text
frontend/
├── src/
│   ├── types/             # TypeScript типы
│   ├── config/            # Константы (API_BASE, WS_BASE)
│   ├── constants/         # Мок-данные
│   ├── context/           # AppContext — глобальное состояние
│   ├── hooks/             # useAlertsWs, useUnitWs
│   ├── api/               # REST-запросы
│   ├── components/        # WorkshopCard, UnitCard, BottomNav, Fab, details/*
│   ├── pages/             # DashboardPage, WorkshopPage, DetailsPage
│   ├── App.tsx
│   ├── main.tsx
│   └── index.css
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
└── netlify.toml
```

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
