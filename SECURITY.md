# Политика безопасности

## Purpose
Краткие правила хранения секретов, аутентификации и доступа к backend-эндпоинтам на основе текущей структуры репозитория.

## Table of contents
- [Purpose](#purpose)
- [Аутентификация и авторизация](#аутентификация-и-авторизация)
- [JWT-токены](#jwt-токены)
- [WebSocket аутентификация](#websocket-аутентификация)
- [Secrets and keys](#secrets-and-keys)
- [Android keystore](#android-keystore)
- [Backend exposure](#backend-exposure)
- [CI/CD status](#cicd-status)

## Аутентификация и авторизация

Система использует stateless JWT-аутентификацию через Spring Security OAuth2 Resource Server:

- **Сессии**: полностью stateless, сервер не хранит сессии.
- **CSRF**: отключен, т.к. используются Bearer-токены в заголовке `Authorization`, а не cookie.
- **Роли**: `ADMIN`, `USER` (и другие, настраиваются через таблицу `roles`).
- **Доступ**: админские эндпоинты (`/admin/**`) защищены через `@PreAuthorize("hasRole('ADMIN')")`.
- **Пароли**: хешируются через `BCryptPasswordEncoder`.
- **Авто-админ**: при первом запуске создается пользователь `admin` со случайным паролем, если БД пуста (`AdminBootstrapConfig`).

Детали потока аутентификации: [AUTH_FLOW.md](AUTH_FLOW.md).

## JWT-токены

### Access token

- Формат: JWT, алгоритм HS256.
- Срок действия: 15 минут.
- Содержимое: `sub` (userId), `role` (роль пользователя), `iss` (`scada-mobile`), `aud` (`scada-mobile-api`).
- Секрет: env-переменная `SCADA_MOBILE_JWT_ACCESS_SECRET` (минимум 256 бит Base64).

### Refresh token

- Формат: случайный UUID.
- Срок действия: 7 дней.
- Хранение: в БД как SHA-256 хеш (таблица `refresh_tokens`).
- Секрет: env-переменная `SCADA_MOBILE_JWT_REFRESH_SECRET`.
- Ротация: при обновлении access token выдается новая пара access + refresh, старый refresh token отзывается.
- Отзыв: возможен принудительный отзыв через флаг `revoked` в БД.

### Валидация

- `JwtDecoder` проверяет подпись, issuer, audience, срок действия.
- `JwtAuthenticationConverter` извлекает роль из claim `role` с префиксом `ROLE_`.
- `AudienceValidator` выполняет дополнительную проверку audience.

## WebSocket аутентификация

WebSocket-соединения аутентифицируются через JWT в query-параметре:

- URL: `/ws/live?token=<jwt>` или `/ws/unit/{unitId}?token=<jwt>`.
- `WebSocketJwtInterceptor` извлекает и валидирует токен при handshake.
- При невалидном токене соединение отклоняется с кодом 401.
- `userId` сохраняется в атрибутах сессии WebSocket.

## Secrets and keys

- Не коммитьте токены, пароли, приватные ключи, keystore (`*.jks`, `*.keystore`) и любые credentials.
- Production значения (URL, отпечатки сертификатов, API-ключи, JWT-секреты) должны задаваться через env/CI-секреты, без хардкода.
- Файлы с секретами (`.env`, `local.properties`, keystore) должны быть исключены в [/.gitignore](.gitignore).
- JWT-секреты должны быть минимум 256 бит (32 байта) в кодировке Base64.

## Android keystore

- Keystore создается один раз и хранится вне репозитория.
- SHA-256 отпечаток публичного ключа публикуется в [frontend/public/well-known/assetlinks.json](frontend/public/well-known/assetlinks.json).
- При смене ключа требуется обновить `assetlinks.json` и перевыпустить APK (см. [android/README.md](android/README.md)).

## Backend exposure

- В dev-профиле Swagger UI и `/v3/api-docs` включены только для разработки (см. [backend/OPENAPI_SETUP.md](backend/OPENAPI_SETUP.md)).
- В prod-профиле OpenAPI и Swagger UI отключены (см. [backend/OPENAPI_SETUP.md](backend/OPENAPI_SETUP.md)).
- Actuator endpoints в prod должны быть закрыты от внешней сети (подробнее в [backend/LOGGING.md](backend/LOGGING.md)).
- CORS в prod: строгий whitelist через `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS`.

## CI/CD status

В репозитории нет настроенных CI/CD пайплайнов или GitHub Actions. Если пайплайны будут добавляться, обеспечить секрет-сканирование и контроль уязвимостей на уровне CI.
