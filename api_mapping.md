# Transport Mapping (SCADA Mobile)

## Purpose
Документ описывает распределение данных по транспортам (REST и WebSocket) и причины такого разделения. Полные схемы сообщений — в [API_REFERENCE.md](API_REFERENCE.md).

## Table of contents
- [Purpose](#purpose)
- [Transport roles](#transport-roles)
- [Subscription model](#subscription-model)
- [Caching strategy](#caching-strategy)
- [Source documents](#source-documents)

## Transport roles
- REST отдает статическую топологию (цехи, автоматы, список устройств), которая меняется только при изменении конфигурации. Реализация в `Controller` и `WorkshopService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L120-L213), [backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L120-L179)).
- `/ws/live` агрегирует глобальные live-алерты и статус автоматов подписанного цеха ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L92-L239)).
- `/ws/unit/{unitId}` используется только для деталей одного автомата и обновляется после каждого polling-цикла ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L138-L199)).

## Subscription model
Подписка на цех реализована клиентскими действиями `SUBSCRIBE_WORKSHOP` и `UNSUBSCRIBE_WORKSHOP`, отправляемыми из `useLiveWs` ([frontend/src/hooks/useLiveWs.ts](frontend/src/hooks/useLiveWs.ts#L66-L141)) и обрабатываемыми `LiveWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L116-L167)).

## Caching strategy
ETag вычисляется из конфигурации топологии и используется для всех REST topology-эндпоинтов ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L89-L113)). Проверка `If-None-Match` выполняется в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L120-L179)).

## Source documents
- Полные схемы сообщений и полей: [API_REFERENCE.md](API_REFERENCE.md).
- Источники данных PrintSrv и правила извлечения: [FRONTEND_DATA_SOURCES.md](FRONTEND_DATA_SOURCES.md).