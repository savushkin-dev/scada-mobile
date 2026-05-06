# Архитектура уведомлений (SCADA Mobile)

## Purpose
Документ описывает реализованный механизм уведомлений: live-алерты по WebSocket и реакцию UI. Системные push-уведомления в текущей версии отсутствуют.

## Table of contents
- [Purpose](#purpose)
- [Backend pipeline](#backend-pipeline)
- [WebSocket delivery](#websocket-delivery)
- [Frontend handling](#frontend-handling)
- [Service worker scope](#service-worker-scope)
- [Current limitations](#current-limitations)

## Backend pipeline
1. `UnitDetailService.extractActiveErrors` извлекает активные ошибки по scada-флагам и составу устройств аппарата ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/UnitDetailService.java#L304-L354)).
2. `UnitErrorStore` хранит активные ошибки как единый источник правды для алертов и вкладки Журнал ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/UnitErrorStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/UnitErrorStore.java#L11-L71)).
3. `AlertService` формирует `AlertMessageDTO` на основе `UnitErrorStore` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java#L18-L141)).
4. `ActiveAlertStore` вычисляет дельту появления/исчезновения алертов для рассылки ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java#L11-L69)).
5. `StatusBroadcaster` отправляет `ALERT` и `UNITS_STATUS` по `/ws/live` и обновления по `/ws/unit/{unitId}` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/StatusBroadcaster.java#L71-L153)).

## WebSocket delivery
- Канал `/ws/live` обслуживается `LiveWsHandler` и рассылает `ALERT_SNAPSHOT`, `ALERT`, `UNITS_STATUS` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L92-L239)).
- Канал `/ws/unit/{unitId}` обслуживается `UnitWsHandler`, отправляет четыре типа сообщений деталей аппарата ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L83-L199)).

## Frontend handling
- `useLiveWs` открывает единый канал `/ws/live` и маршрутизирует `ALERT_SNAPSHOT`, `ALERT`, `UNITS_STATUS` ([frontend/src/hooks/useLiveWs.ts](frontend/src/hooks/useLiveWs.ts#L31-L141)).
- `AppContext` хранит карту активных алертов и обновляет ее при `ALERT` и `ALERT_SNAPSHOT` ([frontend/src/context/AppContext.tsx](frontend/src/context/AppContext.tsx#L51-L210)).
- `RootLayout` инициирует вибрацию при появлении активного алерта, учитывая видимость страницы и антиспам-кулдаун ([frontend/src/layouts/RootLayout.tsx](frontend/src/layouts/RootLayout.tsx#L90-L129)).
- Паттерн вибрации и кулдаун заданы в runtime-конфиге ([frontend/src/config/runtime.ts](frontend/src/config/runtime.ts#L58-L67)).

## Service worker scope
`service-worker.js` реализует cache-стратегию и сетевые запросы API, но не содержит обработчиков `push` или `notificationclick` ([frontend/public/service-worker.js](frontend/public/service-worker.js#L1-L77)).

## Current limitations
- Алерты отправляются только по WebSocket, системные push-уведомления не реализованы (см. [frontend/public/service-worker.js](frontend/public/service-worker.js#L1-L77)).
- Дельта-логика `ActiveAlertStore` фиксирует только появление и исчезновение алерта; изменение состава ошибок при активном алерте не порождает новое сообщение ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/store/ActiveAlertStore.java#L52-L69)).