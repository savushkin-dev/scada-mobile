# Поток данных в backend (SCADA Mobile)

## Purpose
Детальная диаграмма потока данных от источника (PrintSrv) до потребителя (frontend), с указанием всех промежуточных этапов преобразования.

## Table of contents
- [Purpose](#purpose)
- [Общая диаграмма потока](#общая-диаграмма-потока)
- [Этап 1: Опрос PrintSrv](#этап-1-опрос-printsrv)
- [Этап 2: Сохранение снапшота](#этап-2-сохранение-снапшота)
- [Этап 3: Вычисление алертов](#этап-3-вычисление-алертов)
- [Этап 4: WebSocket-рассылка](#этап-4-websocket-рассылка)
- [Этап 5: Получение frontend](#этап-5-получение-frontend)
- [Поток данных деталей автомата](#поток-данных-деталей-автомата)
- [Поток аутентификации](#поток-аутентификации)

## Общая диаграмма потока

```mermaid
flowchart LR
    subgraph External["Внешняя система"]
        PrintSrv["PrintSrv<br/>(TCP сервер маркировки)"]
    end

    subgraph Backend["Backend: Spring Boot"]
        direction TB
        Polling["Polling Layer<br/>PrintSrvPollingRuntime"]
        Mapper["Anti-Corruption Layer<br/>PrintSrvMapper"]
        Snapshot["Snapshot Store<br/>InMemoryInstanceSnapshotStore"]
        ErrorStore["UnitErrorStore"]
        AlertService["AlertService"]
        AlertStore["ActiveAlertStore"]
        Broadcaster["StatusBroadcaster"]
        LiveWs["LiveWsHandler<br/>/ws/live"]
        UnitWs["UnitWsHandler<br/>/ws/unit/{unitId}"]
        UnitDetail["UnitDetailService"]
        Workshop["WorkshopService"]
        Controller["REST Controller"]
    end

    subgraph Client["Клиент"]
        Frontend["Frontend PWA"]
    end

    PrintSrv -->|"TCP QueryAll<br/>каждые 1000мс"| Polling
    Polling -->|"QueryAllResponseDTO"| Mapper
    Mapper -->|"DeviceSnapshot<br/>UnitSnapshot"| Snapshot
    Snapshot --> ErrorStore
    ErrorStore --> AlertService
    AlertService --> AlertStore
    AlertStore --> Broadcaster
    Broadcaster -->|"ALERT<br/>ALERT_SNAPSHOT<br/>UNITS_STATUS"| LiveWs
    Snapshot --> UnitDetail
    UnitDetail -->|"LINE_STATUS<br/>DEVICES_STATUS<br/>QUEUE<br/>ERRORS"| UnitWs
    Snapshot --> Workshop
    Workshop -->|"ETag-cached topology"| Controller
    Controller -->|"REST /api/v1.0.0/*"| Frontend
    LiveWs -->|"WebSocket"| Frontend
    UnitWs -->|"WebSocket"| Frontend
```

## Этап 1: Опрос PrintSrv

```mermaid
sequenceDiagram
    participant Runtime as PrintSrvPollingRuntime
    participant Poller as PrintSrvInstancePoller
    participant Client as PrintSrvClient
    participant PrintSrv as PrintSrv (TCP)

    loop Каждые 1000мс (prod)
        Runtime->>Poller: Запустить опрос instanceId
        Poller->>Client: queryAll("Line")
        Client->>PrintSrv: TCP: {"DeviceName":"Line","Command":"QueryAll"}
        PrintSrv-->>Client: JSON со всеми тегами
        Client-->>Poller: QueryAllResponseDTO
    end
```

- `PrintSrvPollingRuntime` управляет жизненным циклом polling-воркеров (SmartLifecycle).
- Каждый инстанс PrintSrv опрашивается в отдельном виртуальном потоке.
- Интервал опроса: 1000мс в prod, настраивается через `printsrv.polling.fixed-delay-ms`.

## Этап 2: Сохранение снапшота

```mermaid
sequenceDiagram
    participant Poller as PrintSrvInstancePoller
    participant Mapper as PrintSrvMapper
    participant Snapshot as InMemoryInstanceSnapshotStore

    Poller->>Mapper: toDomainDeviceSnapshot(dto)
    Mapper-->>Poller: DeviceSnapshot
    Poller->>Snapshot: save(snapshot)
    Note over Snapshot: ConcurrentHashMap<br/>instanceId → snapshot
```

- `PrintSrvMapper` изолирует домен от протокола PrintSrv (Anti-Corruption Layer).
- `InMemoryInstanceSnapshotStore` — thread-safe хранилище на `ConcurrentHashMap`.
- При недоступности PrintSrv snapshot очищается (graceful degradation).

## Этап 3: Вычисление алертов

```mermaid
sequenceDiagram
    participant Event as PrintSrvInstancePolledEvent
    participant Broadcaster as StatusBroadcaster
    participant ErrorStore as UnitErrorStore
    participant AlertService as AlertService
    participant AlertStore as ActiveAlertStore

    Event->>Broadcaster: onApplicationEvent(event)
    Broadcaster->>ErrorStore: Обновить ошибки по instanceId
    Broadcaster->>AlertService: Вычислить алерты
    AlertService->>AlertStore: Сравнить с предыдущим состоянием
    AlertStore-->>AlertService: Дельта (появились/исчезли)
```

- `UnitErrorStore` — единый источник правды об активных ошибках.
- `AlertService` вычисляет `AlertMessageDTO` с severity (текущая реализация: только `Critical`).
- `ActiveAlertStore` отслеживает дельту: отправляет `ALERT` только при появлении или исчезновении.

## Этап 4: WebSocket-рассылка

```mermaid
sequenceDiagram
    participant Broadcaster as StatusBroadcaster
    participant LiveWs as LiveWsHandler
    participant Client1 as Клиент 1
    participant Client2 as Клиент 2

    Broadcaster->>LiveWs: ALERT (unitId, severity, errors)
    LiveWs->>Client1: {"type":"ALERT",...}
    LiveWs->>Client2: {"type":"ALERT",...}

    Broadcaster->>LiveWs: UNITS_STATUS (workshopId, statuses)
    LiveWs->>Client1: {"type":"UNITS_STATUS",...}
    LiveWs->>Client2: {"type":"UNITS_STATUS",...}
```

- `LiveWsHandler` мультиплексирует сообщения для всех подключенных клиентов.
- Клиенты подписываются на конкретный цех через `SUBSCRIBE_WORKSHOP`.
- `UnitWsHandler` обслуживает per-unit канал для детальных данных.

## Этап 5: Получение frontend

```mermaid
sequenceDiagram
    participant Frontend as Frontend
    participant LiveWs as /ws/live
    participant UnitWs as /ws/unit/{unitId}
    participant REST as REST API

    Frontend->>REST: GET /workshops/topology (с ETag)
    REST-->>Frontend: WorkshopTopologyDTO[] (или 304)

    Frontend->>LiveWs: SUBSCRIBE_WORKSHOP workshopId
    LiveWs-->>Frontend: ALERT_SNAPSHOT
    LiveWs-->>Frontend: UNITS_STATUS (периодически)

    Frontend->>UnitWs: Подключение /ws/unit/{unitId}
    UnitWs-->>Frontend: LINE_STATUS, DEVICES_STATUS, QUEUE, ERRORS
```

## Поток данных деталей автомата

```mermaid
flowchart LR
    Snapshot["InMemoryInstanceSnapshotStore"]
    UnitDetail["UnitDetailService"]
    UnitWs["UnitWsHandler"]
    Frontend["Frontend"]

    Snapshot -->|"BatchQueue<br/>Line<br/>scada<br/>PrinterXX<br/>CamXX"| UnitDetail
    UnitDetail -->|"LINE_STATUS"| UnitWs
    UnitDetail -->|"DEVICES_STATUS"| UnitWs
    UnitDetail -->|"QUEUE"| UnitWs
    UnitDetail -->|"ERRORS"| UnitWs
    UnitWs --> Frontend
```

- `UnitDetailService` извлекает данные из снапшота и формирует 4 типа сообщений.
- При подключении отправляется начальный пакет из 4 сообщений (`sendInitialSnapshot`).
- Канал push-only: клиент только получает данные.

## Поток аутентификации

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant Auth as AuthController
    participant AuthService as AuthService
    participant DB as БД (users, refresh_tokens)

    Client->>Auth: POST /auth/login<br/>{workerCode, password}
    Auth->>AuthService: authenticate(code, password)
    AuthService->>DB: findByCode
    DB-->>AuthService: UserEntity
    AuthService->>AuthService: BCrypt.checkpw
    AuthService->>AuthService: generateAccessToken
    AuthService->>AuthService: generateRefreshToken → SHA-256 hash
    AuthService->>DB: save refresh token hash
    AuthService-->>Auth: AuthLoginResponseDTO
    Auth-->>Client: {accessToken, refreshToken, userId, role}
```

Подробнее: [AUTH_FLOW.md](AUTH_FLOW.md).
