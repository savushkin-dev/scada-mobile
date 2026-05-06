# Middleware в backend: что реально используется

## Purpose
Краткое описание фактических middleware-компонентов в backend и их роль в обработке запросов.

## Table of contents
- [Purpose](#purpose)
- [HTTP filter (MDC)](#http-filter-mdc)
- [CORS configuration](#cors-configuration)
- [Request flow](#request-flow)
- [What is not present](#what-is-not-present)

## HTTP filter (MDC)
Фильтр `MdcFilter` добавляет `requestId`, `method`, `uri` в MDC для каждого HTTP-запроса и возвращает `X-Request-ID` в ответе (см. [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcFilter.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcFilter.java)). Регистрация фильтра с наивысшим приоритетом выполняется в [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcConfig.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcConfig.java).

## CORS configuration
Глобальная CORS-политика задается через `CorsConfig` и `CorsProperties` и читается из `application-*.yaml` (см. [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsConfig.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsConfig.java), [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsProperties.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsProperties.java), [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml), [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml), [backend/src/main/resources/application-prod.yaml](backend/src/main/resources/application-prod.yaml)).

## Request flow
Фактический порядок обработки HTTP-запроса:

1. `MdcFilter` (HIGHEST_PRECEDENCE) — добавляет MDC-контекст.
2. CORS фильтр, созданный Spring на основе `CorsConfig`.
3. `DispatcherServlet` и контроллеры Spring MVC.

## What is not present
- В проекте нет кастомных `HandlerInterceptor` и Spring Security фильтров.
- Дополнительные middleware (rate limiting, auth) пока не реализованы.