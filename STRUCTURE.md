# Архитектура проекта SCADA Mobile

Актуальность: 30.03.2026.

## Что это за проект

SCADA Mobile помогает сотрудникам быстро понимать, что происходит на производственной линии:

- где возникла остановка;
- по какой причине она возникла;
- активна проблема сейчас или уже устранена.

Система построена так, чтобы работать в реальных условиях производства: нестабильная сеть, много устройств и высокая цена задержки информации.

## Из каких частей состоит система

### Backend

- Назначение: принять данные из PrintSrv, собрать их в понятную доменную модель и отдать клиентам.
- Технологии: Java 21, Spring Boot 4, WebSocket, REST API, OpenAPI.
- Где смотреть детали: [backend/PRINTSERV_API.md](backend/PRINTSERV_API.md), [FRONTEND_API.md](FRONTEND_API.md), [api_mapping.md](api_mapping.md).

### Frontend

- Назначение: показать сотрудникам текущее состояние цехов, аппаратов и ошибок в понятном интерфейсе.
- Технологии: React 18, TypeScript 5 (strict), Vite 6, Zod, PWA.
- Где смотреть детали: [frontend/README.md](frontend/README.md), [frontend/UI_UX_SPEC.md](frontend/UI_UX_SPEC.md).

### Android

- Назначение: запуск того же веб-клиента на Android через Trusted Web Activity.
- Технологии: Bubblewrap + Gradle + TWA.
- Где смотреть детали: [android/README.md](android/README.md).

## Как проходит поток данных

1. Событие происходит на оборудовании.
2. PrintSrv читает состояние и передает данные в backend.
3. Backend нормализует данные и определяет состояние инцидента.
4. Frontend получает:
- статическую топологию через REST;
- живые изменения через WebSocket.
5. Android-приложение отображает тот же интерфейс, что и веб-клиент.

## Фактическая структура репозитория

```text
scada-mobile/
├── backend/                 # Spring Boot сервис
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── build.gradle.kts
│   ├── LOGGING.md
│   ├── OPENAPI_SETUP.md
│   ├── PRINTSERV_API.md
│   ├── DDD_REVIEW.md
│   └── WRITE_THROUGH_CACHE_IMPLEMENTATION.md
├── frontend/                # React + TypeScript PWA
│   ├── src/
│   ├── public/
│   ├── package.json
│   ├── vite.config.ts
│   ├── README.md
│   └── UI_UX_SPEC.md
├── android/                 # TWA-оболочка
│   └── README.md
├── docker-compose.yml
├── docker-compose.dev.yml
├── docker-compose.prod.yml
├── Makefile
├── README.md
├── PROJECT_DIAGRAM.md
├── FRONTEND_API.md
├── FRONTEND_DATA_SOURCES.md
├── DOCKER_DEPLOY.md
├── RUN_PROJECT_DOCKER.md
└── SECURITY.md
```

## Принципы разработки

- Backend хранит доменную логику и проверку данных.
- Frontend не дублирует доменную логику backend и не хранит секреты.
- Android-слой не содержит бизнес-логики, только контейнер для web.
- Конфигурация окружений выносится во внешние переменные и YAML, без жестких привязок к локальной машине.
- Документация ведется без дублирования: у каждого файла своя зона ответственности.

## Что является источником истины

- По запуску и командам: [Makefile](Makefile).
- По API и событиям: [FRONTEND_API.md](FRONTEND_API.md) и [api_mapping.md](api_mapping.md).
- По источникам данных backend: [FRONTEND_DATA_SOURCES.md](FRONTEND_DATA_SOURCES.md) и [backend/PRINTSERV_API.md](backend/PRINTSERV_API.md).
- По Docker: [DOCKER_DEPLOY.md](DOCKER_DEPLOY.md) и [RUN_PROJECT_DOCKER.md](RUN_PROJECT_DOCKER.md).
- По безопасности: [SECURITY.md](SECURITY.md).

## Карта документации

- [README.md](README.md): короткий вход в проект и быстрый запуск.
- [PROJECT_DIAGRAM.md](PROJECT_DIAGRAM.md): визуальные схемы системы.
- [PLAN.md](PLAN.md): текущий план работ и этапы.
- [PLAN_BUSINESS.md](PLAN_BUSINESS.md): бизнес-цели внедрения.
- [NOTIFICATIONS_ARCHITECTURE.md](NOTIFICATIONS_ARCHITECTURE.md): архитектура уведомлений.
- [backend/middleware-explanation.md](backend/middleware-explanation.md): роль filter/interceptor/CORS в backend.
- [backend/MOCK_DATA.md](backend/MOCK_DATA.md): моковые данные для разработки и тестов.
