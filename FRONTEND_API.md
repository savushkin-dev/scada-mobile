# Frontend API Overview

## Purpose
Короткое описание того, как фронтенд использует REST и WebSocket. Полный контракт полей и сообщений находится в [API_REFERENCE.md](API_REFERENCE.md).

## Table of contents
- [Purpose](#purpose)
- [Transport overview](#transport-overview)
- [Client configuration](#client-configuration)
- [Implementation references](#implementation-references)

## Transport overview
- Статическая топология цехов и аппаратов загружается через REST (см. [API_REFERENCE.md](API_REFERENCE.md#rest-endpoints)).
- Live-алерты и статус цеха приходят через единый канал `/ws/live` (см. [API_REFERENCE.md](API_REFERENCE.md#websocket-wslive)).
- Детальные данные аппарата приходят через `/ws/unit/{unitId}` (см. [API_REFERENCE.md](API_REFERENCE.md#websocket-wsunitunitid)).

## Client configuration
Базовые URL задаются через `VITE_API_BASE` и `VITE_WS_BASE`, дефолты вычисляются из origin ([frontend/src/config/runtime.ts](frontend/src/config/runtime.ts#L14-L22), [frontend/src/schemas/env.ts](frontend/src/schemas/env.ts#L9-L19)).

## Implementation references
- `useLiveWs` обрабатывает входящие сообщения `/ws/live` ([frontend/src/hooks/useLiveWs.ts](frontend/src/hooks/useLiveWs.ts#L31-L141)).
- `AppContext` хранит карту алертов и live-статусы ([frontend/src/context/AppContext.tsx](frontend/src/context/AppContext.tsx#L51-L210)).
- Источники данных PrintSrv для backend описаны в [FRONTEND_DATA_SOURCES.md](FRONTEND_DATA_SOURCES.md).
- Архитектурное разделение транспортов — в [api_mapping.md](api_mapping.md).