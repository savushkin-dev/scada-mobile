# Transport Mapping (SCADA Mobile)

## Purpose
Документ описывает распределение данных по транспортам (REST и WebSocket) и причины такого разделения. Полные схемы сообщений — в [API_REFERENCE.md](API_REFERENCE.md).

## Table of contents
- [Purpose](#purpose)
- [Transport roles](#transport-roles)
- [Subscription model](#subscription-model)
- [Caching strategy](#caching-strategy)
- [Authentication](#authentication)
- [Source documents](#source-documents)

## Transport roles

### REST API

REST отдает статические данные, которые меняются только при изменении конфигурации или явном действии пользователя:

| Эндпоинт | Данные | Кеширование |
|----------|--------|-------------|
| `GET /workshops/topology` | Список цехов | ETag |
| `GET /workshops/{id}/units/topology` | Список автоматов цеха | ETag |
| `GET /workshops/{id}/units/{unitId}/devices/topology` | Устройства автомата | ETag |
| `GET /users/me` | Профиль пользователя | ETag |
| `GET /notifications/settings` | Настройки уведомлений | ETag |
| `PUT /notifications/settings` | Обновление настроек | — |
| `POST /line/{unitId}/last-batch` | Переключение уведомления | — |
| `GET /health/live` | Liveness проба | — |
| `GET /health/ready` | Readiness проба | — |
| `POST /auth/login` | Вход | — |
| `POST /auth/logout` | Выход | — |
| `POST /auth/refresh` | Ротация токенов | — |

Реализация: `Controller`, `AuthController`, `NotificationController`, `NotificationSettingsController`, `UserProfileController` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/)) и `WorkshopService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L89-L179)).

### WebSocket `/ws/live`

Агрегирует глобальные live-данные, обновляемые после каждого polling-цикла:

| Сообщение | Когда отправляется | Содержимое |
|-----------|-------------------|------------|
| `ALERT_SNAPSHOT` | При подключении клиента | Все активные алерты |
| `ALERT` | При появлении/исчезновении алерта | Конкретный алерт |
| `UNITS_STATUS` | При изменении статуса автоматов | Статусы автоматов подписанного цеха |
| `NOTIFICATION_SNAPSHOT` | При подключении | Все активные производственные уведомления |
| `NOTIFICATION` | При активации/деактивации | Конкретное уведомление |

Реализация: `LiveWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L92-L239)).

### WebSocket `/ws/unit/{unitId}`

Используется только для деталей одного автомата, обновляется после каждого polling-цикла:

| Сообщение | Содержимое |
|-----------|------------|
| `LINE_STATUS` | Информация о партии |
| `DEVICES_STATUS` | Статусы устройств |
| `QUEUE` | Очередь партий |
| `ERRORS` | Активные ошибки + журнал |

Реализация: `UnitWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L138-L199)).

## Subscription model

Подписка на цех реализована клиентскими действиями:

```json
{"action": "SUBSCRIBE_WORKSHOP", "workshopId": "dess"}
```

Отправляется из `useLiveWs` ([frontend/src/hooks/useLiveWs.ts](frontend/src/hooks/useLiveWs.ts#L66-L141)) при входе на экран цеха и обрабатывается `LiveWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L116-L167)).

При выходе с экрана цеха отправляется:

```json
{"action": "UNSUBSCRIBE_WORKSHOP"}
```

## Caching strategy

ETag вычисляется из конфигурации топологии и используется для всех REST topology-эндпоинтов ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L89-L113)). Проверка `If-None-Match` выполняется в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L120-L179)).

Frontend кеширует топологию в `AppContext` и использует ETag для условных запросов.

## Authentication

### REST API

Все запросы (кроме `/auth/**` и `/actuator/health`) требуют заголовка:

```
Authorization: Bearer <accessToken>
```

### WebSocket

Токен передается в query-параметре при handshake:

```
/ws/live?token=<accessToken>
/ws/unit/{unitId}?token=<accessToken>
```

`WebSocketJwtInterceptor` валидирует токен и отклоняет соединение при невалидном токене (401).

## Source documents
- Полные схемы сообщений и полей: [API_REFERENCE.md](API_REFERENCE.md).
- Источники данных PrintSrv и правила извлечения: [FRONTEND_DATA_SOURCES.md](FRONTEND_DATA_SOURCES.md).
- Архитектура backend: [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md).
- Поток данных: [BACKEND_DATA_FLOW.md](BACKEND_DATA_FLOW.md).
- Аутентификация: [AUTH_FLOW.md](AUTH_FLOW.md).
