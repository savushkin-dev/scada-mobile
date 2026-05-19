# Middleware в backend

## Purpose
Краткое описание фактических middleware-компонентов в backend и их роль в обработке запросов.

## Table of contents
- [Purpose](#purpose)
- [HTTP filter (MDC)](#http-filter-mdc)
- [CORS configuration](#cors-configuration)
- [Security filter chain](#security-filter-chain)
- [JWT authentication](#jwt-authentication)
- [WebSocket JWT interceptor](#websocket-jwt-interceptor)
- [Request flow](#request-flow)
- [What is not present](#what-is-not-present)

## HTTP filter (MDC)

Фильтр `MdcFilter` добавляет `requestId`, `method`, `uri` в MDC для каждого HTTP-запроса и возвращает `X-Request-ID` в ответе ([backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcFilter.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcFilter.java)). Регистрация фильтра с наивысшим приоритетом выполняется в [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcConfig.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcConfig.java).

## CORS configuration

Глобальная CORS-политика задается через `CorsConfig` и `CorsProperties` и читается из `application-*.yaml` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsConfig.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsConfig.java), [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsProperties.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/CorsProperties.java)).

- Dev-профиль: разрешены `localhost`, `192.168.*`, `10.*`, ngrok-origins.
- Prod-профиль: строгий whitelist через `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS`.

## Security filter chain

Spring Security настроен в `SecurityConfig` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/config/SecurityConfig.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/SecurityConfig.java)):

- Stateless-сессии (без серверных сессий).
- CSRF отключен (используются Bearer-токены, не cookie).
- Публичные эндпоинты: `/auth/**`, `/actuator/health`, `/ws/**`.
- Админские эндпоинты: `/admin/**` требуют `ROLE_ADMIN`.
- Все остальные запросы: требуется аутентификация.
- Пароли хешируются через `BCryptPasswordEncoder`.

## JWT authentication

Архитектура аутентификации:

- **Access token**: JWT HS256, 15 минут, содержит `sub` (userId) и `role`.
- **Refresh token**: случайный UUID, хранится в БД как SHA-256 хеш, 7 дней.
- **Секреты**: `SCADA_MOBILE_JWT_ACCESS_SECRET` и `SCADA_MOBILE_JWT_REFRESH_SECRET` (env-переменные, минимум 256 бит Base64).
- **Валидация**: `JwtDecoder` проверяет подпись, issuer (`scada-mobile`), audience (`scada-mobile-api`), срок действия.
- **Ротация**: при истечении access token клиент отправляет refresh token, получает новую пару.

Детали: [AUTH_FLOW.md](../AUTH_FLOW.md), [SECURITY.md](../SECURITY.md).

## WebSocket JWT interceptor

`WebSocketJwtInterceptor` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/config/jwt/WebSocketJwtInterceptor.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/jwt/WebSocketJwtInterceptor.java)):

- Извлекает `?token=<jwt>` из query-параметра URL WebSocket.
- Валидирует токен через `JwtTokenProvider`.
- Сохраняет `userId` в атрибутах сессии.
- Отклоняет анонимные соединения с кодом 401.

## Request flow

Фактический порядок обработки HTTP-запроса:

1. `MdcFilter` (HIGHEST_PRECEDENCE) — добавляет MDC-контекст.
2. CORS фильтр, созданный Spring на основе `CorsConfig`.
3. Spring Security filter chain (JWT validation).
4. `DispatcherServlet` и контроллеры Spring MVC.

## What is not present

- Нет кастомных `HandlerInterceptor` (кроме WebSocket JWT interceptor).
- Нет rate limiting.
- Нет audit-логирования запросов.
