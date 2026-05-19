# Жизненный цикл алерта (SCADA Mobile)

## Purpose
Документ описывает полный жизненный цикл производственного алерта: от возникновения события на оборудовании до отображения пользователю и снятия.

## Table of contents
- [Purpose](#purpose)
- [Общая диаграмма](#общая-диаграмма)
- [Этап 1: Возникновение события на оборудовании](#этап-1-возникновение-события-на-оборудовании)
- [Этап 2: Опрос PrintSrv](#этап-2-опрос-printsrv)
- [Этап 3: Обнаружение ошибки](#этап-3-обнаружение-ошибки)
- [Этап 4: Вычисление алерта](#этап-4-вычисление-алерта)
- [Этап 5: Рассылка по WebSocket](#этап-5-рассылка-по-websocket)
- [Этап 6: Отображение на frontend](#этап-6-отображение-на-frontend)
- [Этап 7: Снятие алерта](#этап-7-снятие-алерта)
- [Временные характеристики](#временные-характеристики)

## Общая диаграмма

```mermaid
flowchart LR
    subgraph Equipment["Оборудование"]
        Line["Линия маркировки"]
        Printer["Принтер"]
        Cam["Камера"]
    end

    subgraph PrintSrv["PrintSrv"]
        Tags["Теги состояния<br/>ST, Error, ErrorMessage"]
    end

    subgraph Backend["Backend"]
        Polling["Polling<br/>каждые 1000мс"]
        Snapshot["Snapshot Store"]
        ErrorStore["UnitErrorStore"]
        AlertService["AlertService"]
        AlertStore["ActiveAlertStore"]
        Broadcaster["StatusBroadcaster"]
        LiveWs["LiveWsHandler"]
    end

    subgraph Frontend["Frontend"]
        AppCtx["AppContext"]
        Dashboard["DashboardPage"]
        Workshop["WorkshopPage"]
        UnitCard["UnitCard"]
        Vibration["Вибрация"]
    end

    Line -->|"Ошибка/стоп"| Tags
    Printer -->|"Ошибка печати"| Tags
    Cam -->|"Брак"| Tags

    Tags -->|"TCP QueryAll"| Polling
    Polling --> Snapshot
    Snapshot --> ErrorStore
    ErrorStore --> AlertService
    AlertService --> AlertStore
    AlertStore -->|"Дельта: новый алерт"| Broadcaster
    Broadcaster -->|"ALERT"| LiveWs
    LiveWs -->|"WebSocket"| AppCtx
    AppCtx --> Dashboard
    AppCtx --> Workshop
    AppCtx --> UnitCard
    AppCtx -->|"Критический алерт"| Vibration
```

## Этап 1: Возникновение события на оборудовании

На производственной линии происходит одно из событий:

| Событие | Источник | Примеры |
|---------|----------|---------|
| Остановка линии | `Line.ST = 0` | Нет заготовок, оператор отвлекся |
| Ошибка линии | `Line.Error = 1` | Механическая неисправность |
| Ошибка принтера | `scada.LineDev011Error = 1` | Замятие ленты, нет чернил |
| Ошибка камеры | `scada.Dev041Error = 1` | Несчитанный код, брак |
| Ошибка checker-камеры | `CamChecker.BatchFailed > 0` | Дефект упаковки |

Эти события фиксируются в тегах PrintSrv в реальном времени.

## Этап 2: Опрос PrintSrv

```mermaid
sequenceDiagram
    participant Poller as PrintSrvInstancePoller
    participant PrintSrv as PrintSrv (TCP)
    participant Mapper as PrintSrvMapper
    participant Snapshot as InMemoryInstanceSnapshotStore

    loop Каждые 1000мс
        Poller->>PrintSrv: queryAll("Line")
        PrintSrv-->>Poller: Все теги всех устройств
        Poller->>Mapper: toDomainDeviceSnapshot(dto)
        Mapper-->>Poller: DeviceSnapshot, UnitSnapshot
        Poller->>Snapshot: save(snapshot)
    end
```

- Backend опрашивает PrintSrv каждую секунду.
- Получает полный снапшот всех устройств (`Line`, `BatchQueue`, `PrinterXX`, `CamXX`, `scada`).
- Снапшот сохраняется в `InMemoryInstanceSnapshotStore`.

## Этап 3: Обнаружение ошибки

```mermaid
sequenceDiagram
    participant Event as PrintSrvInstancePolledEvent
    participant Broadcaster as StatusBroadcaster
    participant UnitDetail as UnitDetailService
    participant ErrorStore as UnitErrorStore

    Event->>Broadcaster: onApplicationEvent(event)
    Broadcaster->>UnitDetail: extractActiveErrors(instanceId, snapshot)
    UnitDetail->>UnitDetail: Анализ scada-флагов<br/>и состава устройств
    UnitDetail-->>Broadcaster: List<DeviceError>
    Broadcaster->>ErrorStore: Обновить ошибки для unitId
```

`UnitDetailService.extractActiveErrors` анализирует:
- `Line.Error`, `Line.ErrorMessage`, `Line.ST`
- `scada.lineerr`
- `scada.LineDev011Error`..`LineDev014Error` (принтеры)
- `scada.Dev041Error`..`Dev044Error` (камеры агрегации)
- `scada.Dev071Error`..`Dev074Error` (EAN-checker)
- Runtime-ошибки `CamChecker` (нет scada-обертки)

## Этап 4: Вычисление алерта

```mermaid
sequenceDiagram
    participant ErrorStore as UnitErrorStore
    participant AlertService as AlertService
    participant AlertStore as ActiveAlertStore

    ErrorStore->>AlertService: getErrorsByUnitId(unitId)
    AlertService->>AlertService: Формирование AlertMessageDTO
    Note over AlertService: severity = Critical<br/>(если есть ошибки)<br/>severity = Warning<br/>(если ST=0 без ошибок)
    AlertService->>AlertStore: compareAndUpdate(alert)
    AlertStore->>AlertStore: Дельта с предыдущим состоянием?
    alt Алерт новый или изменился
        AlertStore-->>AlertService: delta = true
    else Состояние не изменилось
        AlertStore-->>AlertService: delta = false
    end
```

`AlertService` формирует:
- `workshopId`, `unitId`, `unitName` — из топологии
- `severity` — `Critical` (ошибки есть) или `Warning` (стоп без ошибок)
- `active` — `true` (появление) или `false` (снятие)
- `errors[]` — список `AlertErrorDTO` (device, code, message)
- `timestamp` — ISO-8601

`ActiveAlertStore` отслеживает дельту:
- Отправляет `ALERT` только при появлении или исчезновении алерта.
- Изменение состава ошибок при активном алерте **не** порождает новое сообщение.

## Этап 5: Рассылка по WebSocket

```mermaid
sequenceDiagram
    participant Broadcaster as StatusBroadcaster
    participant LiveWs as LiveWsHandler
    participant Client1 as Клиент 1 (мастер)
    participant Client2 as Клиент 2 (админ)

    Broadcaster->>LiveWs: ALERT {workshopId, unitId, severity, active, errors}

    alt Клиент подписан на этот цех
        LiveWs->>Client1: {"type":"ALERT",...}
    else Клиент не подписан
        LiveWs-xClient2: (не отправляется)
    end

    Broadcaster->>LiveWs: UNITS_STATUS {workshopId, statuses}
    LiveWs->>Client1: {"type":"UNITS_STATUS",...}
    LiveWs->>Client2: {"type":"UNITS_STATUS",...}
```

- `ALERT` отправляется только клиентам, подписанным на цех (`SUBSCRIBE_WORKSHOP`).
- `UNITS_STATUS` отправляется всем клиентам, подписанным на цех.
- При подключении нового клиента отправляется `ALERT_SNAPSHOT` — полный срез активных алертов.

## Этап 6: Отображение на frontend

```mermaid
sequenceDiagram
    participant LiveWs as useLiveWs
    participant AppCtx as AppContext
    participant Dashboard as DashboardPage
    participant Workshop as WorkshopPage
    participant UnitCard as UnitCard
    participant RootLayout as RootLayout

    LiveWs->>AppCtx: ALERT_SNAPSHOT
    AppCtx->>AppCtx: alerts = snapshot

    LiveWs->>AppCtx: ALERT (active=true)
    AppCtx->>AppCtx: alerts[unitId] = alert
    AppCtx->>Dashboard: Перерисовка
    AppCtx->>Workshop: Перерисовка
    AppCtx->>UnitCard: Красная/желтая граница
    AppCtx->>RootLayout: Триггер вибрации

    LiveWs->>AppCtx: UNITS_STATUS
    AppCtx->>AppCtx: unitsByWorkshop[workshopId] = statuses
    AppCtx->>Workshop: Обновление бейджей и таймеров
```

### Визуальные индикаторы

| Элемент | Состояние | Индикация |
|---------|-----------|-----------|
| Карточка цеха | Норма | Зеленая статичная граница |
| Карточка цеха | Предупреждение | Желтая пульсирующая граница |
| Карточка цеха | Критично | Красная пульсирующая граница |
| Карточка автомата | Норма | Зеленый бейдж, нет таймера |
| Карточка автомата | Проблема | Красный/желтый бейдж, таймер простоя |
| Шапка деталей | Ошибка | Красная пульсирующая точка |
| Вибрация | Критический алерт | Паттерн вибрации (кулдаун 5 сек) |

### Вибрация

- Срабатывает при появлении активного критического алерта.
- Учитывает видимость страницы (`document.visibilityState`).
- Антиспам-кулдаун: 5 секунд между вибрациями.
- Не срабатывает при обновлении страницы или переподключении WebSocket.

## Этап 7: Снятие алерта

```mermaid
sequenceDiagram
    participant Equipment as Оборудование
    participant PrintSrv as PrintSrv
    participant Backend as Backend
    participant Frontend as Frontend

    Equipment->>PrintSrv: Ошибка устранена<br/>Error = 0, ST = 1
    PrintSrv-->>Backend: Следующий QueryAll
    Backend->>Backend: extractActiveErrors → пустой список
    Backend->>Backend: AlertService → active=false
    Backend->>Backend: ActiveAlertStore → дельта (исчез)
    Backend-->>Frontend: ALERT {active=false}
    Frontend->>Frontend: Удалить алерт из карты
    Frontend->>Frontend: Обновить UI (зеленый статус)
```

Алерт считается снятым, когда:
- `Line.Error = 0` и `Line.ST = 1`
- Все `scada.*Error = 0`
- Все runtime-ошибки устройств устранены

При снятии отправляется `ALERT` с `active=false`. Frontend удаляет алерт из карты и обновляет UI.

## Временные характеристики

| Этап | Время | Примечание |
|------|-------|------------|
| Возникновение → PrintSrv | < 1 сек | Зависит от оборудования |
| PrintSrv → Backend (опрос) | ≤ 1000 мс | Интервал polling |
| Backend → Frontend (WebSocket) | < 100 мс | Локальная сеть |
| **Общая задержка** | **≤ 2 сек** | От события до отображения |

В dev-профиле с mock-данными задержка может быть меньше из-за ускоренного polling.
