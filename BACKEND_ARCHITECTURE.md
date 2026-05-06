# Backend Architecture (SCADA Mobile)

## Purpose
Документ описывает текущую серверную архитектуру: polling PrintSrv, snapshot-хранилище, вычисление алертов и WebSocket-рассылку.

## Table of contents
- [Purpose](#purpose)
- [Runtime flow](#runtime-flow)
- [Polling and snapshots](#polling-and-snapshots)
- [Topology and REST API](#topology-and-rest-api)
- [Alerts and /ws/live](#alerts-and-wslive)
- [Unit details /ws/unit/{unitId}](#unit-details-wsunitunitid)
- [Health checks](#health-checks)

## Runtime flow
1. `PrintSrvPollingRuntime` запускает отдельный worker на каждый инстанс PrintSrv и публикует событие после успешного опроса ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java#L18-L90)).
2. Каждый worker выполняет `PrintSrvInstancePoller.poll`, считывает устройства, маппит и сохраняет snapshot в репозиторий ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java#L74-L121)).
3. `StatusBroadcaster` получает `PrintSrvInstancePolledEvent`, обновляет store ошибок и транслирует live-данные ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java#L71-L153)).
4. `LiveWsHandler` рассылает `ALERT`, `ALERT_SNAPSHOT` и `UNITS_STATUS` по каналу `/ws/live` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L92-L239)).
5. `UnitWsHandler` формирует данные деталей аппарата и пушит `LINE_STATUS`, `DEVICES_STATUS`, `QUEUE`, `ERRORS` по `/ws/unit/{unitId}` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L127-L199)).

## Polling and snapshots
- Polling-оркестратор использует виртуальные потоки и фиксированную задержку между циклами, см. `PrintSrvPollingRuntime` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvPollingRuntime.java#L60-L90)).
- `PrintSrvInstancePoller` опрашивает каждый device через `PrintSrvClient.queryAll` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/client/PrintSrvClient.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/client/PrintSrvClient.java#L1-L60)), маппит ответ `PrintSrvMapper` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/PrintSrvMapper.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/integration/printsrv/PrintSrvMapper.java#L14-L113)) и сохраняет через `InstanceSnapshotRepository` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/application/ports/InstanceSnapshotRepository.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/application/ports/InstanceSnapshotRepository.java#L17-L61)).
- При недоступности всех устройств инстанса snapshot очищается, чтобы UI сразу показал отсутствие данных ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/polling/PrintSrvInstancePoller.java#L102-L118)).

## Topology and REST API
- Базовый путь REST задается через `scada.api.base-path` в [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml#L1-L8).
- Контроллер REST находится в `Controller` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L120-L213)) и отдает статическую топологию с ETag.
- `WorkshopService` вычисляет ETag и собирает топологию из конфигурации ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L89-L179)).

## Alerts and /ws/live
- `UnitDetailService.extractActiveErrors` строит список активных ошибок по scada-флагам с учетом состава устройств ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java#L304-L354)).
- `UnitErrorStore` хранит активные ошибки как единый источник правды для алертов и вкладки Журнал ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/UnitErrorStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/UnitErrorStore.java#L11-L71)).
- `AlertService` вычисляет `AlertMessageDTO` на основе `UnitErrorStore` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java#L18-L141)).
- `ActiveAlertStore` фиксирует активные алерты и формирует дельту появления/снятия ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java#L11-L78)).
- `StatusBroadcaster` отправляет `ALERT` и `UNITS_STATUS` через `LiveWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java#L84-L139)).

## Unit details /ws/unit/{unitId}
- `UnitWsHandler` обслуживает канал `/ws/unit/{unitId}` и отправляет четыре типа сообщений, используя `UnitDetailService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L138-L199)).
- `UnitDetailService` формирует `LINE_STATUS`, `DEVICES_STATUS`, `QUEUE`, `ERRORS` из snapshot-хранилища ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java#L27-L206)).

## Health checks
- Liveness и readiness формируются контроллером ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L215-L259)) на основе `HealthService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java#L22-L39)).
