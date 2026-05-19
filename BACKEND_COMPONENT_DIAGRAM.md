# Компонентная диаграмма backend (SCADA Mobile)

## Purpose
Визуальная карта слоев, пакетов и ключевых компонентов backend с указанием зависимостей между ними.

## Table of contents
- [Purpose](#purpose)
- [Диаграмма слоев](#диаграмма-слоев)
- [Описание слоев](#описание-слоев)
- [Поток данных между компонентами](#поток-данных-между-компонентами)

## Диаграмма слоев

```mermaid
flowchart TB
    subgraph Clients["Клиенты"]
        Web["Frontend PWA"]
        Android["Android TWA"]
    end

    subgraph API["Presentation Layer (api)"]
        Controllers["REST Controllers"]
        DTOs["Request/Response DTOs"]
        ApiMapper["ApiMapper"]
    end

    subgraph Services["Service Layer (services)"]
        AuthService["AuthService"]
        AlertService["AlertService"]
        WorkshopService["WorkshopService"]
        UnitDetailService["UnitDetailService"]
        NotificationService["NotificationService"]
        UserProfileService["UserProfileService"]
        HealthService["HealthService"]
    end

    subgraph Application["Application Layer (application)"]
        ScadaAppService["ScadaApplicationService"]
        Ports["Ports (interfaces)"]
    end

    subgraph Domain["Domain Layer (domain)"]
        Models["Domain Models"]
    end

    subgraph Infra["Infrastructure Layer (infrastructure)"]
        subgraph DB["Database Integration"]
            JPAEntities["JPA Entities"]
            Repositories["Spring Data Repositories"]
            Adapters["JPA Adapters"]
        end

        subgraph PrintSrv["PrintSrv Integration"]
            TcpClient["TcpPrintSrvClient"]
            MockClient["MockPrintSrvClient (dev)"]
            Mapper["PrintSrvMapper"]
            DTO["PrintSrv DTOs"]
        end

        subgraph Polling["Polling"]
            Poller["PrintSrvInstancePoller"]
            Runtime["PrintSrvPollingRuntime"]
            Events["PrintSrvInstancePolledEvent"]
        end

        subgraph WS["WebSocket"]
            LiveWs["LiveWsHandler (/ws/live)"]
            UnitWs["UnitWsHandler (/ws/unit/*)"]
            Broadcaster["StatusBroadcaster"]
        end

        subgraph Store["In-Memory Stores"]
            SnapshotStore["InstanceSnapshotStore"]
            ErrorStore["UnitErrorStore"]
            AlertStore["ActiveAlertStore"]
            NotificationStore["InMemoryNotificationStore"]
        end
    end

    subgraph Config["Configuration (config)"]
        Security["SecurityConfig + JWT"]
        Cors["CorsConfig"]
        WebSocket["WebSocketConfig"]
    end

    Web --> Controllers
    Web --> LiveWs
    Web --> UnitWs
    Android --> Web

    Controllers --> Services
    Controllers --> Application
    DTOs --> Controllers

    Services --> Application
    Services --> Domain
    Services --> Infra

    Application --> Domain
    Application --> Ports

    Ports --> Adapters

    Adapters --> Repositories
    Repositories --> JPAEntities

    Runtime --> Poller
    Poller --> TcpClient
    Poller --> MockClient
    Poller --> Mapper
    Poller --> SnapshotStore
    Poller --> Events

    Events --> Broadcaster
    Broadcaster --> AlertStore
    Broadcaster --> ErrorStore
    Broadcaster --> NotificationStore
    Broadcaster --> LiveWs
    Broadcaster --> UnitWs

    LiveWs --> Web
    UnitWs --> Web

    Config --> Security
    Config --> Cors
    Config --> WebSocket
```

## Описание слоев

### Presentation Layer (`api`)

| Компонент | Назначение |
|-----------|------------|
| `AuthController` | Аутентификация: login, logout, refresh |
| `Controller` | Топология (workshops, units, devices), health checks |
| `NotificationController` | Управление производственными уведомлениями |
| `NotificationSettingsController` | Настройки уведомлений пользователя |
| `UserProfileController` | Профиль текущего пользователя |
| `Admin*Controller` | CRUD операции для администратора |
| DTOs | Строго типизированные request/response объекты |
| `ApiMapper` | Маппинг между DTO и domain models |

### Service Layer (`services`)

| Компонент | Назначение |
|-----------|------------|
| `AuthService` | Аутентификация, валидация паролей, генерация JWT-пар |
| `AlertService` | Вычисление активных алертов из `UnitErrorStore` |
| `WorkshopService` | Топология, ETag, агрегация статусов цехов |
| `UnitDetailService` | Формирование детальных данных автомата |
| `NotificationService` | Жизненный цикл производственных уведомлений |
| `UserProfileService` | Профиль пользователя с назначенными автоматами |
| `HealthService` | Liveness/readiness пробы |
| `DeviceCompositionService` | Определение состава устройств автомата |
| `UnitMappingService` | Маппинг DB unitId ↔ PrintSrv instanceId |
| `ScadaKeyMapper` | Маппинг имен устройств PrintSrv → scada-ключи |

### Application Layer (`application`)

| Компонент | Назначение |
|-----------|------------|
| `ScadaApplicationService` | Координация use-cases |
| Ports (`*Repository`) | Интерфейсы для инфраструктурных адаптеров |

### Domain Layer (`domain`)

| Компонент | Назначение |
|-----------|------------|
| `AuthUser` | Идентификация пользователя |
| `UserProfile` | Профиль с назначенными автоматами |
| `DeviceSnapshot` | Снапшот состояния устройства |
| `UnitSnapshot` | Снапшот состояния автомата |
| `ProductionNotification` | Производственное уведомление |
| `UserNotificationSettings` | Настройки уведомлений |

### Infrastructure Layer (`infrastructure`)

| Компонент | Назначение |
|-----------|------------|
| JPA Entities | ORM-модели для PostgreSQL |
| JPA Repositories | Spring Data JPA интерфейсы |
| JPA Adapters | Реализация application ports |
| `TcpPrintSrvClient` | TCP-клиент для PrintSrv (prod) |
| `MockPrintSrvClient` | Mock-клиент с XML seed-файлами (dev) |
| `PrintSrvMapper` | ACL: PrintSrv DTO → domain models |
| `PrintSrvPollingRuntime` | Оркестрация polling-воркеров |
| `PrintSrvInstancePoller` | Опрос одного инстанса PrintSrv |
| `LiveWsHandler` | WebSocket `/ws/live` |
| `UnitWsHandler` | WebSocket `/ws/unit/{unitId}` |
| `StatusBroadcaster` | Рассылка событий подписчикам WebSocket |
| `InMemoryInstanceSnapshotStore` | Хранилище снапшотов |
| `UnitErrorStore` | Хранилище активных ошибок |
| `ActiveAlertStore` | Кеш вычисленных алертов |
| `InMemoryNotificationStore` | Хранилище производственных уведомлений |

## Поток данных между компонентами

1. **Polling**: `PrintSrvPollingRuntime` → `PrintSrvInstancePoller` → `PrintSrvClient.queryAll` → `PrintSrvMapper` → `InstanceSnapshotRepository.save`
2. **Alert computation**: `PrintSrvInstancePolledEvent` → `StatusBroadcaster` → `UnitErrorStore` → `AlertService` → `ActiveAlertStore`
3. **Broadcast**: `StatusBroadcaster` → `LiveWsHandler` → клиенты (`ALERT`, `UNITS_STATUS`)
4. **Unit details**: клиент → `UnitWsHandler` → `UnitDetailService` → `InstanceSnapshotRepository` → `LINE_STATUS`, `DEVICES_STATUS`, `QUEUE`, `ERRORS`
5. **REST API**: клиент → `Controller` → `WorkshopService` / `UserProfileService` → JPA Adapters → БД
