# Диаграмма структуры проекта (Mermaid)

Ниже две диаграммы:

1) **Архитектура и потоки данных** — как связаны PWA/Android, Backend и PrintSrv.
2) **Структура репозитория** — укрупнённая карта папок/документов.

> Источник правды для этой диаграммы: `STRUCTURE.md`.

## 1) Архитектура и потоки данных

```mermaid
flowchart TB
  %% ====== Clients ======
  subgraph Clients[Клиенты]
    WebPWA["Frontend PWA<br/>React + TypeScript<br/>Vite"]
    AndroidTWA["Android TWA<br/>Контейнер для PWA"]
  end

  %% ====== Backend ======
  subgraph Backend["Backend: Spring Boot / Java"]
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

  %% ====== Notes ======
  classDef boundary fill:#f7f7f7,stroke:#777,stroke-width:1px;
  class Clients,Backend,External boundary;
```

## 2) Укрупнённая структура репозитория

```mermaid
flowchart LR
  Root["scada-mobile/"]

  Root --> BE["backend/<br/>Spring Boot сервер"]
  Root --> FE["frontend/<br/>React PWA"]
  Root --> AND["android/<br/>Android TWA wrapper"]
  Root --> GH[".github/<br/>workflows CI/CD"]

  Root --> Docs["Документация"]
  Docs --> Readme["README.md<br/>Quick Start"]
  Docs --> Structure["STRUCTURE.md<br/>архитектура/структура"]
  Docs --> Arch["ARCHITECTURE.md<br/>архитектурные решения"]
  Docs --> Sec["SECURITY.md<br/>политика безопасности"]
  Docs --> AndDoc["ANDROID.md<br/>сборка APK/AAB"]
  Docs --> Key["KEYSTORE.md<br/>опционально: инструкции keystore"]

  FE --> PWA1["manifest.webmanifest"]
  FE --> PWA2["service-worker.js"]
  FE --> WellKnown[".well-known/assetlinks.json"]

  GH --> WF1["backend.yml"]
  GH --> WF2["frontend.yml"]
  GH --> WF3["security.yml"]
  GH --> WF4["secrets-scan.yml"]
```
