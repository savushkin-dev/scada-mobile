# Backend Architecture (SCADA Mobile)

## Purpose
Документ описывает текущую серверную архитектуру: polling PrintSrv, snapshot-хранилище, вычисление алертов, WebSocket-рассылку, аутентификацию и администрирование.

## Table of contents
- [Purpose](#purpose)
- [Runtime flow](#runtime-flow)
- [Polling and snapshots](#polling-and-snapshots)
- [Topology and REST API](#topology-and-rest-api)
- [Alerts and /ws/live](#alerts-and-wslive)
- [Unit details /ws/unit/{unitId}](#unit-details-wsunitunitid)
- [Production notifications](#production-notifications)
- [Authentication and security](#authentication-and-security)
- [Admin panel](#admin-panel)
- [Health checks](#health-checks)

## Runtime flow

1. `PrintSrvPollingRuntime` запускает отдельный виртуальный поток на каждый инстанс PrintSrv и публикует событие `PrintSrvInstancePolledEvent` после успешного опроса ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java#L18-L90)).
2. Каждый worker выполняет `PrintSrvInstancePoller.poll`, считывает объекты PrintSrv, маппит через `PrintSrvMapper` и сохраняет snapshot в репозиторий ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java#L74-L121)).
3. `StatusBroadcaster` получает `PrintSrvInstancePolledEvent`, обновляет `UnitErrorStore` и транслирует live-данные ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java#L71-L153)).
4. `LiveWsHandler` рассылает `ALERT`, `ALERT_SNAPSHOT`, `UNITS_STATUS` и `NOTIFICATION` по каналу `/ws/live` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L92-L239)).
5. `UnitWsHandler` формирует данные деталей автомата и пушит `LINE_STATUS`, `DEVICES_STATUS`, `QUEUE`, `ERRORS` по `/ws/unit/{unitId}` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L127-L199)).

## Polling and snapshots

- Polling-оркестратор использует виртуальные потоки и фиксированную задержку между циклами, см. `PrintSrvPollingRuntime` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java#L60-L90)).
- `PrintSrvInstancePoller` опрашивает каждый unit/объект через `PrintSrvClient.queryAll` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/client/PrintSrvClient.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/client/PrintSrvClient.java#L1-L60)), маппит ответ `PrintSrvMapper` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/PrintSrvMapper.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/PrintSrvMapper.java#L14-L113)) и сохраняет через `InstanceSnapshotRepository` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/application/ports/InstanceSnapshotRepository.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/application/ports/InstanceSnapshotRepository.java#L17-L61)).
- При недоступности всех объектов инстанса snapshot очищается, чтобы UI сразу показал отсутствие данных ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java#L102-L118)).
- В dev-профиле работает `MockPrintSrvClient` с XML seed-файлами и `MockStateSimulator` для симуляции изменений состояния.

## Topology and REST API

- Базовый путь REST задается через `scada.api.base-path` в [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml#L1-L8).
- Контроллер REST находится в `Controller` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L120-L213)) и отдает статическую топологию с ETag.
- `WorkshopService` вычисляет ETag и собирает топологию из конфигурации ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L89-L179)).
- Аутентификация через `AuthController`: login, logout, refresh token rotation.
- Профиль пользователя через `UserProfileController` с ETag-кешированием.
- Настройки уведомлений через `NotificationSettingsController`.

## Alerts and /ws/live

- `UnitDetailService.extractActiveErrors` строит список активных ошибок по scada-флагам с учетом состава объектов ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java#L304-L354)).
- `UnitErrorStore` хранит активные ошибки как единый источник правды для алертов и вкладки Журнал ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/UnitErrorStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/UnitErrorStore.java#L11-L71)).
- `AlertService` вычисляет `AlertMessageDTO` на основе `UnitErrorStore` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java#L18-L141)).
- `ActiveAlertStore` фиксирует активные алерты и формирует дельту появления/снятия ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java#L11-L78)).
- `StatusBroadcaster` отправляет `ALERT` и `UNITS_STATUS` через `LiveWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java#L84-L139)).
- Клиенты подписываются на цех через `SUBSCRIBE_WORKSHOP` / `UNSUBSCRIBE_WORKSHOP`.

## Unit details /ws/unit/{unitId}

- `UnitWsHandler` обслуживает канал `/ws/unit/{unitId}` и отправляет четыре типа сообщений, используя `UnitDetailService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L138-L199)).
- `UnitDetailService` формирует `LINE_STATUS`, `DEVICES_STATUS`, `QUEUE`, `ERRORS` из snapshot-хранилища ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java#L27-L206)).
- Канал push-only: входящие сообщения от клиента игнорируются.

## Production notifications

- Производственные уведомления ("последняя партия") реализованы через `NotificationService`.
- Пользователь отправляет `POST /api/v1.0.0/line/{unitId}/last-batch` — backend активирует или деактивирует уведомление.
- `InMemoryNotificationStore` хранит активные уведомления.
- `NotificationStateChangedEvent` рассылается через `StatusBroadcaster` → `LiveWsHandler` как `NOTIFICATION` / `NOTIFICATION_SNAPSHOT`.
- Видимость FAB-кнопки на frontend контролируется через `AccessControlContext` (пользователь должен быть назначен на автомат).

## Authentication and security

- Stateless JWT-аутентификация через Spring Security OAuth2 Resource Server.
- Access token: JWT HS256, 15 минут, содержит `sub` (userId) и `role`.
- Refresh token: UUID, хранится в БД как SHA-256 хеш, 7 дней.
- WebSocket аутентификация через `?token=<jwt>` в query-параметре.
- Пароли: BCrypt. Админские эндпоинты: `@PreAuthorize("hasRole('ADMIN')")`.
- Подробнее: [AUTH_FLOW.md](AUTH_FLOW.md), [SECURITY.md](SECURITY.md), [backend/middleware-explanation.md](backend/middleware-explanation.md).

## Admin panel

- REST API для администрирования под `/api/v1.0.0/admin/**`.
- CRUD для: пользователей, ролей, цехов, автоматов, устройств, типов устройств, назначений, настроек уведомлений.
- `AdminReadController` — read-only списки для админ-панели.
- Frontend использует React Admin с кастомным `dataProvider`.

## Health checks

- Liveness и readiness формируются контроллером ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L215-L259)) на основе `HealthService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java#L22-L39)).
- Liveness: всегда `UP`.
- Readiness: `UP` только после получения первого snapshot от PrintSrv.
