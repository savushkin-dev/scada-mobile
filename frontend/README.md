# SCADA Mobile — Frontend

Фронтенд-часть проекта находится в этой папке (`frontend/`).

## Текущее состояние

Сейчас — статический PWA-прототип (HTML/CSS/JS) с подключённым Service Worker и Web Manifest. Служит дизайн-прототипом и обеспечивает интеграцию TWA (Digital Asset Links).

> Целевая архитектура фронтенда (React 18 + TypeScript + Vite) описана в [`STRUCTURE.md`](../STRUCTURE.md).
> UI/UX спецификация (экраны, поведение, состояния) — в [`UI_UX_SPEC.md`](UI_UX_SPEC.md).
> Описание API и транспортных каналов — в [`api_mapping.md`](../api_mapping.md).

## Ключевые файлы

```text
frontend/
├── app.html               # Входная страница прототипа
├── manifest.webmanifest   # Web App Manifest
├── service-worker.js      # Service Worker (offline cache)
├── netlify.toml           # Конфигурация деплоя Netlify
├── well-known/
│   └── assetlinks.json    # Digital Asset Links (связь с Android-приложением)
└── assets/
    ├── icons/             # Иконки приложения
    └── screenshots/       # Скриншоты для сторов
```

## Быстрый запуск (прототип)

```bash
cd frontend
python -m http.server 5500
```

Откройте в браузере: `http://localhost:5500/app.html`

> Используйте порт **5500** — он включён в CORS-allowlist dev-профиля бэкенда. При использовании Live Server в VS Code порт 5500 устанавливается автоматически.

## PWA и Digital Asset Links

- `manifest.webmanifest` — метаданные приложения, `start_url`, иконки
- `service-worker.js` — стратегия cache-first для offline-режима
- `well-known/assetlinks.json` — связывает веб-сайт с Android-приложением (убирает адресную строку в TWA)

> Настройка Digital Asset Links и сборка Android-приложения — в [`android/README.md`](../android/README.md).
