# Архитектура проекта SCADA Mobile

Мобильное SCADA-приложение для мониторинга и управления технологическими процессами. Реализовано как Progressive Web App (PWA) с нативной Android-оберткой через TWA.

## Технологический стек

### Backend

- **Java 17+** с **Spring Boot 3.x**
- **WebSocket** для real-time коммуникации с устройствами
- **TCP сокеты** для интеграции с PrintSrv (сервер выступает клиентом; пакетирование: `P001` + длина + JSON)
- **Spring Data JPA** для работы с БД
- **MapStruct** — маппинг между Entity и DTO
- **OpenAPI / Swagger** — документация API и генерация типов

### Frontend

- **React 18** + **TypeScript 5** (strict mode)
- **Vite** — сборщик и dev-сервер
- **React Query** — работа с серверными данными
- **Zod** — валидация схем данных
- **PWA** (Service Worker, Web Manifest)
- **ESLint + Prettier** — качество кода

### Mobile

- **Android TWA** (Trusted Web Activity) — нативная обертка вокруг PWA
- **Bubblewrap** — инструмент для сборки TWA-приложений
- Никакой дополнительной логики — просто контейнер для веб-приложения

## Структура проекта

```text
scada-mobile/
│
├── backend/                    # Spring Boot сервер
│   ├── src/main/java/         # Исходники
│   ├── src/main/resources/    # Конфигурация
│   ├── build.gradle           # Gradle конфигурация
│   └── README.md              # Документация бэкенда
│
├── frontend/                   # React PWA приложение
│   ├── src/                   # Исходники приложения
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   └── api/              # Сгенерированные типы из OpenAPI
│   │
│   ├── public/                # Статические файлы
│   ├── .well-known/           
│   │   └── assetlinks.json   # Связь с Android приложением
│   │
│   ├── assets/               # PWA ресурсы
│   │   ├── icons/           # Иконки приложения
│   │   └── screenshots/     # Скриншоты для сторов
│   │
│   ├── manifest.webmanifest  # PWA манифест
│   ├── service-worker.js     # Service Worker
│   ├── package.json
│   ├── vite.config.ts
│   ├── eslint.config.js
│   ├── prettier.config.js
│   └── README.md
│
├── android/                   # Android TWA обертка
│   ├── app/                  # Исходники приложения
│   ├── keystore/             # ⚠️ НЕ КОММИТИТСЯ В GIT!
│   ├── twa-manifest.json     # Конфигурация TWA
│   ├── build.gradle
│   └── README.md
│
├── .github/
│   └── workflows/            # CI/CD пайплайны
│       ├── backend.yml       # Сборка и тесты бэкенда
│       ├── frontend.yml      # Сборка и проверки фронтенда
│       ├── security.yml      # Сканирование безопасности
│       └── secrets-scan.yml  # Проверка на утечку секретов
│
├── .husky/                   # Git hooks
├── .editorconfig             # Настройки форматирования
├── .gitignore
├── .gitattributes            # Нормализация переносов строк
│
├── README.md                 # Общая документация проекта
├── ARCHITECTURE.md           # Описание архитектуры
├── SECURITY.md               # Политика безопасности
└── ANDROID.md                # Инструкции по сборке APK
```

## Принципы разработки

### 🎨 Frontend (PWA)

**Главное правило:** Фронтенд — это только UI. Никакой бизнес-логики, никаких секретов.

#### TypeScript

- Строгий режим (`strict: true`)
- Запрет на `any` — используй `unknown` с type guards
- Все типы API автогенерируются из OpenAPI спецификации

#### Валидация данных

- **Zod** для runtime-валидации всех данных от сервера
- Схемы Zod можно генерировать из OpenAPI или писать вручную

#### Работа с API

- **React Query** для всех серверных запросов
- Централизованная обработка ошибок
- Автоматическая повторная попытка и кэширование

#### PWA требования

Обязательные файлы для работы PWA:

- `manifest.webmanifest` — метаданные приложения
- `service-worker.js` — offline-режим и кэширование
- `.well-known/assetlinks.json` — связь с Android-приложением
- `assets/icons/` — иконки всех размеров
- `assets/screenshots/` — скриншоты для магазинов

#### Качество кода

- **ESLint** — статический анализ
- **Prettier** — форматирование
- **Husky + lint-staged** — проверки перед коммитом

### ⚙️ Backend (Spring Boot)

**Главное правило:** Вся бизнес-логика, валидация и безопасность — здесь.

#### Архитектура

- **Controller → Service → Repository** — стандартная структура
- **DTO ≠ Entity** — всегда разделяем слои
- **MapStruct** — автоматический маппинг между DTO и Entity

#### Валидация

- **Bean Validation (JSR 380)** на всех входящих данных
- `@Valid` / `@Validated` на контроллерах
- Кастомные валидаторы где нужно

#### Обработка ошибок

- **Problem+JSON (RFC 7807)** — стандартизированный формат ошибок API
- `@ControllerAdvice` — централизованная обработка исключений
- Единообразные структуры ответов для всех типов ошибок (валидация, бизнес-логика, системные)

#### API

- **OpenAPI 3.0** (springdoc-openapi) — автогенерация документации
- Аннотации на контроллерах для описания эндпоинтов
- Экспорт спецификации в JSON/YAML → генерация TypeScript моделей

#### Real-time коммуникация

- **WebSocket** для связи с устройствами и фронтендом
- ETag не используется (не имеет смысла для WebSocket)

#### Интеграция с PrintSrv (TCP)

PrintSrv (сервер маркировки) является источником данных (тегов) для SCADA-системы. Бэкенд (Spring Boot) выступает **TCP-клиентом** по отношению к PrintSrv и общается с ним через сокеты.

> Полная документация протокола PrintSrv — формат фрейма, команды, теги, типичные ошибки — в [`backend/PRINTSERV_API.md`](backend/PRINTSERV_API.md).

#### Форматирование кода

⚠️ **НЕ используем** Spotless / Checkstyle — они плохо синхронизируются между IDE и CI.

**Используем:** настройки IDE + `.editorconfig` для единообразия.

### 📱 Android (TWA)

**Главное правило:** Android — это просто контейнер для веб-приложения. Никакой логики!

#### Что это?

Trusted Web Activity (TWA) — технология Google, которая оборачивает PWA в нативное приложение без адресной строки браузера.

#### Сборка

- **Bubblewrap** — CLI-утилита от Google для создания и управления TWA
- Генерирует стандартный Android-проект

#### Подписание приложения

- **Keystore** хранится отдельно (не в Git!)

> Инструкции по сборке, подписанию APK и установке — в [`android/README.md`](android/README.md). Правила хранения ключей — в [`SECURITY.md`](SECURITY.md).

## Автоматизация и CI/CD

### 🔄 Генерация типов (Backend → Frontend)

**Цепочка:**

```text
Spring контроллеры с OpenAPI аннотациями
    ↓
springdoc-openapi генерирует openapi.json
    ↓
openapi-typescript-codegen генерирует TypeScript модели
    ↓
(опционально) утилита генерирует Zod схемы
```

**Автоматизация:**

- npm-скрипт `gen:api` в frontend
- Запускается вручную или на pre-commit hook
- Или автоматически в GitHub Actions при изменении бэкенда

### 🔐 Безопасность

Политика безопасности, список инструментов CI (TruffleHog, Dependabot, CodeQL, OWASP) и правила хранения секретов — в [`SECURITY.md`](../SECURITY.md).

#### Git hooks (Husky)

- Pre-commit: ESLint, Prettier, TypeScript type-check
- Pre-push: (опционально) запуск тестов

### 📋 CI/CD пайплайны

#### Backend Pipeline (`backend.yml`)

1. Сборка проекта (Gradle)
2. Запуск тестов
3. OWASP Dependency Check
4. Генерация OpenAPI спецификации
5. (опционально) Деплой

#### Frontend Pipeline (`frontend.yml`)

1. Установка зависимостей
2. ESLint проверка
3. TypeScript type-check
4. Prettier проверка
5. Сборка проекта (Vite)
6. (опционально) Деплой

## Документация

### Минимально необходимые файлы

| Файл | Содержание |
|------|-----------|
| `README.md` | Как запустить проект целиком (Quick Start), текущий статус |
| `STRUCTURE.md` | Целевая архитектура, стек, принципы разработки, CI/CD |
| `PROJECT_DIAGRAM.md` | Визуальные диаграммы архитектуры (Mermaid) |
| `SECURITY.md` | Политика безопасности, где хранятся секреты |
| `PLAN.md` | Технический план разработки по фазам |
| `PLAN_BUSINESS.md` | Бизнес-цели проекта |
| `api_mapping.md` | Контракт API: REST и WebSocket, маппинг полей |
| `android/README.md` | Инструкции по сборке, подписанию APK и установке |
| `frontend/README.md` | Описание frontend-части, быстрый старт |
| `frontend/UI_UX_SPEC.md` | UI/UX спецификация (экраны, поведение, состояния) |
| `backend/PRINTSERV_API.md` | Протокол PrintSrv (TCP, фрейм, команды, теги) |
| `backend/RETRY_MECHANISM.md` | Механизм retry и graceful degradation |
| `backend/LOGGING.md` | Стратегия логирования |
| `backend/OPENAPI_SETUP.md` | Настройка OpenAPI и профилей Spring Boot |
| `backend/DDD_REVIEW.md` | DDD-анализ архитектуры бэкенда |
| `backend/WRITE_THROUGH_CACHE_IMPLEMENTATION.md` | Scan cycle архитектура |

## Стандарты кодирования

### Форматирование

- `.editorconfig` — единые настройки для всех редакторов
- Frontend: Prettier (автоформат)
- Backend: настройки IDE + `.editorconfig`

### Git

- `.gitattributes` — нормализация переносов строк (LF для всех)
- `.gitignore` — исключения (node_modules, build, keystore и т.д.)

### Commits

- Conventional Commits (опционально, но рекомендуется)
- Примеры: `feat:`, `fix:`, `docs:`, `refactor:`

## Чек-лист перед началом разработки

- [ ] Создана структура папок согласно схеме выше
- [ ] Настроен `.editorconfig`
- [ ] Настроены `.gitignore` и `.gitattributes`
- [ ] Установлен Husky + lint-staged
- [ ] Настроены GitHub Actions (backend, frontend, security)
- [ ] Создана документация (README, ARCHITECTURE, SECURITY, ANDROID, KEYSTORE)
- [ ] Настроен Dependabot
- [ ] Keystore создан и НЕ закоммичен в Git
- [ ] Настроена генерация TypeScript моделей из OpenAPI
