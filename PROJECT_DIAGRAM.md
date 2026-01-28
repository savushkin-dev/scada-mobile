# Диаграмма архитектуры проекта (Mermaid)

Ниже представлены две архитектурные схемы:

1) **Упрощенная архитектура (для разработки)** — прямое подключение Backend к PrintSrv через TCP, используется на этапе локальной разработки и тестирования.
2) **Production архитектура** — полная схема с инфраструктурными компонентами (Kafka, Gateway), как интегрируется в общую систему.

> Источник правды для технических деталей: `STRUCTURE.md`.

## 1) Упрощенная архитектура (для разработки)

```mermaid
flowchart TB
  %% ====== Clients ======
  subgraph Clients[Клиенты]
    WebPWA["Frontend PWA<br/>React + TypeScript<br/>Vite"]
    AndroidTWA["Android TWA<br/>Контейнер для PWA"]
  end

  %% ====== Backend ======
  subgraph Backend["Backend: Spring Boot"]
    API["HTTP API<br/>OpenAPI 3.0"]
    WS["WebSocket<br/>real-time для UI/устройств"]
    PrintAdapter["PrintSrv TCP-Client<br/>фрейминг: P001 + length + JSON"]
    Domain["Бизнес-логика<br/>валидация/безопасность"]
    DB[("База данных")]
  end

  %% ====== External ======
  subgraph External["Внешние системы"]
    PrintSrv["PrintSrv<br/>сервер маркировки<br/>источник тегов"]
  end

  %% ====== App composition ======
  AndroidTWA -->|"отображает"| WebPWA

  %% ====== API / realtime ======
  WebPWA <-->|"HTTP REST"| API
  WebPWA <-->|"WebSocket"| WS

  %% ====== Backend internal ======
  API --> Domain
  WS --> Domain
  Domain <-->|"persist/queries"| DB
  Domain --> PrintAdapter

  %% ====== PrintSrv integration ======
  PrintAdapter <-->|"TCP socket<br/>JSON команды<br/>чтение/запись тегов"| PrintSrv

  %% ====== Styling ======
  classDef clientStyle fill:#5b8fb9,stroke:#4a7a9a,stroke-width:2px,color:#fff
  classDef backendStyle fill:#6b9080,stroke:#5a7a6b,stroke-width:2px,color:#fff
  classDef externalStyle fill:#b8945f,stroke:#9a7a4f,stroke-width:2px,color:#fff
  classDef dbStyle fill:#8b7eb8,stroke:#75699a,stroke-width:2px,color:#fff
  
  class WebPWA,AndroidTWA clientStyle
  class API,WS,PrintAdapter,Domain backendStyle
  class PrintSrv externalStyle
  class DB dbStyle
```

## 2) Production архитектура (полная схема)

```mermaid
flowchart TB
  %% ====== Clients ======
  subgraph Clients[Клиенты]
    WebPWA2["Frontend PWA<br/>React + TypeScript<br/>Vite"]
    AndroidTWA2["Android TWA<br/>Контейнер для PWA"]
  end

  %% ====== Backend ======
  subgraph MobileSrv["Mobile Server: Spring Boot"]
    API2["HTTP API<br/>OpenAPI 3.0"]
    WS2["WebSocket<br/>real-time для UI"]
    KafkaConsumer["Kafka Consumer<br/>подписка на события"]
    Domain2["Бизнес-логика<br/>валидация/безопасность"]
    DB2[("База данных")]
  end

  %% ====== Infrastructure ======
  subgraph Infrastructure["Инфраструктура и данные"]
    Kafka["Apache Kafka<br/>шина сообщений"]
  end

  %% ====== Gateway Layer ======
  subgraph GatewayLayer["Шлюзы"]
    Gateway["Gateway<br/>маршрутизация/трансформация"]
  end

  %% ====== SCADA ======
  subgraph SCADA["Контроллеры, SCADA"]
    PrintSrv2["PrintSrv<br/>сервер маркировки<br/>источник тегов"]
  end

  %% ====== App composition ======
  AndroidTWA2 -->|"отображает"| WebPWA2

  %% ====== API / realtime ======
  WebPWA2 <-->|"HTTP REST"| API2
  WebPWA2 <-->|"WebSocket"| WS2

  %% ====== Backend internal ======
  API2 --> Domain2
  WS2 --> Domain2
  Domain2 <-->|"persist/queries"| DB2
  Domain2 --> KafkaConsumer

  %% ====== Production data flow ======
  KafkaConsumer <-->|"события тегов<br/>команды управления"| Kafka
  Kafka <-->|"сообщения"| Gateway
  Gateway <-->|"TCP socket<br/>JSON команды"| PrintSrv2

  %% ====== Styling ======
  classDef clientStyle fill:#5b8fb9,stroke:#4a7a9a,stroke-width:2px,color:#fff
  classDef backendStyle fill:#6b9080,stroke:#5a7a6b,stroke-width:2px,color:#fff
  classDef infraStyle fill:#8b7eb8,stroke:#75699a,stroke-width:2px,color:#fff
  classDef gatewayStyle fill:#7a8a99,stroke:#66747f,stroke-width:2px,color:#fff
  classDef scadaStyle fill:#b8945f,stroke:#9a7a4f,stroke-width:2px,color:#fff
  classDef dbStyle fill:#8b7eb8,stroke:#75699a,stroke-width:2px,color:#fff
  
  class WebPWA2,AndroidTWA2 clientStyle
  class API2,WS2,KafkaConsumer,Domain2 backendStyle
  class Kafka infraStyle
  class Gateway gatewayStyle
  class PrintSrv2 scadaStyle
  class DB2 dbStyle
```

---

**Пояснения:**

- **Упрощенная схема** используется для локальной разработки: Mobile Server напрямую подключается к PrintSrv по TCP (как описано в `STRUCTURE.md`).
- **Production схема** отражает реальную интеграцию: Mobile Server → Kafka → Gateway → PrintSrv. Kafka обеспечивает асинхронную доставку событий, Gateway выполняет маршрутизацию и протокольную трансформацию.
