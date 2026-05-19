# Архитектура проекта SCADA Mobile

Актуальность: 18.05.2026.

## Purpose
Краткая архитектурная карта проекта и ссылки на базовые документы.

## Table of contents
- [Purpose](#purpose)
- [Что это за проект](#что-это-за-проект)
- [Из каких частей состоит система](#из-каких-частей-состоит-система)
- [Как проходит поток данных](#как-проходит-поток-данных)
- [Фактическая структура репозитория](#фактическая-структура-репозитория)
- [Принципы разработки](#принципы-разработки)
- [Что является источником истины](#что-является-источником-истины)
- [Карта документации](#карта-документации)

## Что это за проект

SCADA Mobile помогает сотрудникам быстро понимать, что происходит на производственной линии:

- где возникла остановка;
- по какой причине она возникла;
- активна проблема сейчас или уже устранена.

Система построена так, чтобы работать в реальных условиях производства: нестабильная сеть, много устройств и высокая цена задержки информации.

## Из каких частей состоит система

### Backend

- Назначение: принять данные из PrintSrv, собрать их в понятную доменную модель, управлять пользователями и правами доступа, отдать данные клиентам.
- Технологии: Java 21, Spring Boot 4, Spring Security (JWT), WebSocket, REST API, OpenAPI, PostgreSQL, Flyway.
- Архитектура: Clean Architecture / Ports & Adapters — слои `api` → `services` → `application` → `domain`, инфраструктура в `infrastructure`.
- Где смотреть детали: [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md), [BACKEND_COMPONENT_DIAGRAM.md](BACKEND_COMPONENT_DIAGRAM.md), [BACKEND_DATA_FLOW.md](BACKEND_DATA_FLOW.md), [backend/PRINTSERV_API.md](backend/PRINTSERV_API.md), [API_REFERENCE.md](API_REFERENCE.md).

### Frontend

- Назначение: показать сотрудникам текущее состояние цехов, автоматов и ошибок в понятном интерфейсе; предоставить администратору панель управления.
- Технологии: React 18, TypeScript 5 (strict), Vite 6, Tailwind CSS, Zod, React Router, React Admin, PWA.
- Архитектура: компонентный подход, управление состоянием через React Context, валидация данных через Zod, двухуровневая модель данных (статическая топология + live-статусы).
- Где смотреть детали: [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md), [frontend/README.md](frontend/README.md), [frontend/UI_UX_SPEC.md](frontend/UI_UX_SPEC.md).

### Android

- Назначение: запуск того же веб-клиента на Android через Trusted Web Activity.
- Технологии: Bubblewrap + Gradle + TWA.
- Где смотреть детали: [android/README.md](android/README.md).

## Как проходит поток данных

1. Событие происходит на оборудовании.
2. PrintSrv читает состояние и передает данные в backend по TCP.
3. Backend опрашивает PrintSrv каждую секунду, нормализует данные и определяет состояние инцидента.
4. Backend вычисляет алерты и рассылает live-обновления через WebSocket.
5. Frontend получает:
   - статическую топологию через REST (с ETag-кешированием);
   - живые изменения через WebSocket (`/ws/live` и `/ws/unit/{unitId}`).
6. Android-приложение отображает тот же интерфейс, что и веб-клиент.

## Фактическая структура репозитория

```text
scada-mobile/
├── backend/                 # Spring Boot сервис
│   ├── src/main/java/       # Исходный код (Clean Architecture)
│   ├── src/main/resources/  # Конфигурация, миграции БД, mock-данные
│   ├── build.gradle.kts     # Сборка Gradle (Kotlin DSL)
│   ├── DDD_REVIEW.md        # Оценка архитектуры по DDD
│   ├── LOGGING.md           # Логирование
│   ├── PRINTSERV_API.md     # Протокол PrintSrv
│   ├── MOCK_DATA.md         # Mock-данные для dev
│   ├── OPENAPI_SETUP.md     # OpenAPI/Swagger
│   └── middleware-explanation.md  # Middleware (MDC, CORS, Security)
├── frontend/                # React + TypeScript PWA
│   ├── src/                 # Исходный код
│   ├── public/              # Статика, манифест, Service Worker
│   ├── package.json
│   ├── vite.config.ts
│   ├── README.md            # Обзор фронтенда
│   └── UI_UX_SPEC.md        # UX-спецификация
├── android/                 # TWA-оболочка
│   ├── app/
│   └── README.md
├── docker-compose.yml       # Базовый Docker Compose
├── docker-compose.dev.yml   # Dev-стек
├── docker-compose.prod.yml  # Prod-стек
├── Makefile                 # Команды разработки
├── README.md                # Главный вход в проект
├── STRUCTURE.md             # Этот файл
├── PROJECT_DIAGRAM.md       # Визуальные схемы системы
├── API_REFERENCE.md         # Контракт REST и WebSocket
├── FRONTEND_API.md          # Краткий обзор API для фронтенда
├── FRONTEND_DATA_SOURCES.md # Откуда backend берет данные
├── api_mapping.md           # Разделение транспортов
├── BACKEND_ARCHITECTURE.md  # Runtime архитектура backend
├── BACKEND_COMPONENT_DIAGRAM.md  # Компонентная диаграмма backend
├── BACKEND_DATA_FLOW.md     # Поток данных в backend
├── AUTH_FLOW.md             # Поток аутентификации JWT
├── ALERT_LIFECYCLE.md       # Жизненный цикл алерта
├── FRONTEND_ARCHITECTURE.md # Архитектура frontend
├── NOTIFICATIONS_ARCHITECTURE.md # Архитектура уведомлений
├── DB_NOTIFICATIONS_CONCEPT.md   # ER-диаграмма БД
├── MAKEFILE.md              # Команды Makefile
├── RUN_PROJECT_DOCKER.md    # Запуск в Docker
├── SECURITY.md              # Политика безопасности
├── PLAN.md                  # Технический план
└── PLAN_BUSINESS.md         # Бизнес-план
```

## Принципы разработки

- Backend хранит доменную логику и проверку данных.
- Frontend не дублирует доменную логику backend и не хранит секреты.
- Android-слой не содержит бизнес-логики, только контейнер для web.
- Конфигурация окружений выносится во внешние переменные и YAML, без жестких привязок к локальной машине.
- Документация ведется без дублирования: у каждого файла своя зона ответственности.
- Аутентификация stateless через JWT: access token (15 мин) + refresh token (7 дней).
- WebSocket соединения аутентифицируются через JWT в query-параметре.

## Что является источником истины

- По запуску и командам: [Makefile](Makefile) и [MAKEFILE.md](MAKEFILE.md).
- По API и событиям: [API_REFERENCE.md](API_REFERENCE.md), [FRONTEND_API.md](FRONTEND_API.md) и [api_mapping.md](api_mapping.md).
- По источникам данных backend: [FRONTEND_DATA_SOURCES.md](FRONTEND_DATA_SOURCES.md) и [backend/PRINTSERV_API.md](backend/PRINTSERV_API.md).
- По архитектуре backend: [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md), [BACKEND_COMPONENT_DIAGRAM.md](BACKEND_COMPONENT_DIAGRAM.md), [BACKEND_DATA_FLOW.md](BACKEND_DATA_FLOW.md).
- По архитектуре frontend: [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md).
- По аутентификации: [AUTH_FLOW.md](AUTH_FLOW.md) и [SECURITY.md](SECURITY.md).
- По Docker: [RUN_PROJECT_DOCKER.md](RUN_PROJECT_DOCKER.md).
- По безопасности: [SECURITY.md](SECURITY.md).

## Карта документации

- [README.md](README.md): короткий вход в проект и быстрый запуск.
- [STRUCTURE.md](STRUCTURE.md): архитектурная карта (этот файл).
- [PROJECT_DIAGRAM.md](PROJECT_DIAGRAM.md): визуальные схемы системы.
- [API_REFERENCE.md](API_REFERENCE.md): полный контракт REST и WebSocket.
- [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md): runtime архитектура backend.
- [BACKEND_COMPONENT_DIAGRAM.md](BACKEND_COMPONENT_DIAGRAM.md): компоненты и слои backend.
- [BACKEND_DATA_FLOW.md](BACKEND_DATA_FLOW.md): поток данных от PrintSrv до frontend.
- [AUTH_FLOW.md](AUTH_FLOW.md): аутентификация и авторизация.
- [ALERT_LIFECYCLE.md](ALERT_LIFECYCLE.md): жизненный цикл производственного алерта.
- [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md): архитектура frontend.
- [NOTIFICATIONS_ARCHITECTURE.md](NOTIFICATIONS_ARCHITECTURE.md): архитектура уведомлений.
- [DB_NOTIFICATIONS_CONCEPT.md](DB_NOTIFICATIONS_CONCEPT.md): схема базы данных.
- [frontend/UI_UX_SPEC.md](frontend/UI_UX_SPEC.md): UX-поведение интерфейса.
- [backend/DDD_REVIEW.md](backend/DDD_REVIEW.md): оценка DDD/Clean Architecture.
- [backend/PRINTSERV_API.md](backend/PRINTSERV_API.md): протокол PrintSrv.
- [backend/MOCK_DATA.md](backend/MOCK_DATA.md): mock-данные для разработки.
- [PLAN.md](PLAN.md): текущий план работ и этапы.
- [PLAN_BUSINESS.md](PLAN_BUSINESS.md): бизнес-цели внедрения.
