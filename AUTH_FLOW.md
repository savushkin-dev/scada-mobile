# Поток аутентификации (SCADA Mobile)

## Purpose
Документ описывает полный поток аутентификации и авторизации: от входа пользователя до WebSocket-соединения.

## Table of contents
- [Purpose](#purpose)
- [Общая диаграмма](#общая-диаграмма)
- [Этап 1: Вход (login)](#этап-1-вход-login)
- [Этап 2: Использование access token](#этап-2-использование-access-token)
- [Этап 3: Ротация токенов (refresh)](#этап-3-ротация-токенов-refresh)
- [Этап 4: Выход (logout)](#этап-4-выход-logout)
- [Этап 5: WebSocket аутентификация](#этап-5-websocket-аутентификация)
- [Этап 6: Доступ к админ-панели](#этап-6-доступ-к-админ-панели)
- [Сравнение токенов](#сравнение-токенов)

## Общая диаграмма

```mermaid
flowchart TB
    subgraph Client["Клиент (Frontend)"]
        Login["Экран входа"]
        Storage["localStorage"]
        ApiClient["API Client"]
        WsClient["WebSocket Client"]
    end

    subgraph Backend["Backend (Spring Security)"]
        AuthController["AuthController"]
        AuthService["AuthService"]
        JwtProvider["JwtTokenProvider"]
        JwtDecoder["JwtDecoder"]
        UserRepo["UserAuthRepository"]
        TokenRepo["RefreshTokenRepository"]
        SecurityFilter["Security Filter Chain"]
        WsInterceptor["WebSocketJwtInterceptor"]
    end

    subgraph DB["БД (PostgreSQL)"]
        Users["users"]
        RefreshTokens["refresh_tokens"]
    end

    Login -->|"POST /auth/login<br/>{workerCode, password}"| AuthController
    AuthController --> AuthService
    AuthService --> UserRepo
    UserRepo --> Users
    AuthService --> JwtProvider
    JwtProvider -->|"accessToken (JWT)"| AuthController
    JwtProvider -->|"refreshToken (UUID)"| AuthService
    AuthService --> TokenRepo
    TokenRepo --> RefreshTokens
    AuthController -->|"{accessToken, refreshToken}"| Storage

    Storage -->|"Authorization: Bearer <accessToken>"| ApiClient
    ApiClient -->|"Запрос к API"| SecurityFilter
    SecurityFilter --> JwtDecoder
    JwtDecoder -->|"Валидация подписи,<br/>issuer, audience, expiry"| SecurityFilter

    Storage -->|"?token=<accessToken>"| WsClient
    WsClient -->|"WebSocket handshake"| WsInterceptor
    WsInterceptor --> JwtProvider

    ApiClient -->|"POST /auth/refresh<br/>{refreshToken}"| AuthController
    AuthController --> AuthService
    AuthService --> TokenRepo
    TokenRepo --> RefreshTokens
    AuthService -->|"Новая пара токенов"| AuthController
    AuthController -->|"Обновить в Storage"| Storage
```

## Этап 1: Вход (login)

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant Auth as AuthController
    participant Service as AuthService
    participant UserRepo as UserAuthJpaAdapter
    participant TokenRepo as RefreshTokenJpaAdapter
    participant DB as БД

    Client->>Auth: POST /auth/login<br/>{workerCode, password}
    Auth->>Service: authenticate(workerCode, password)
    Service->>UserRepo: findByCode(workerCode)
    UserRepo->>DB: SELECT * FROM users WHERE code = ?
    DB-->>UserRepo: UserEntity
    UserRepo-->>Service: Optional<UserEntity>

    alt Пользователь не найден
        Service-->>Auth: AuthenticationException
        Auth-->>Client: 401 Unauthorized
    else Пользователь найден
        Service->>Service: BCrypt.checkpw(password, hash)
        alt Пароль неверный
            Service-->>Auth: AuthenticationException
            Auth-->>Client: 401 Unauthorized
        else Пароль верный
            Service->>Service: generateAccessToken(userId, role)
            Note over Service: JWT HS256, 15 мин<br/>sub=userId, role=ROLE_*
            Service->>Service: generateRefreshToken()
            Note over Service: UUID, 7 дней
            Service->>Service: SHA-256(refreshToken)
            Service->>TokenRepo: save(tokenHash, userId, expiresAt)
            TokenRepo->>DB: INSERT INTO refresh_tokens
            TokenRepo-->>Service: RefreshTokenEntity
            Service-->>Auth: AuthLoginResponseDTO
            Auth-->>Client: 200 OK<br/>{accessToken, refreshToken, userId, code, fullName, role}
        end
    end
```

### Поля ответа login

| Поле | Описание |
|------|----------|
| `status` | `"success"` |
| `userId` | Числовой ID пользователя |
| `code` | Worker code (логин) |
| `fullName` | ФИО сотрудника |
| `role` | Роль (`ADMIN`, `USER`) |
| `accessToken` | JWT для доступа к API |
| `refreshToken` | UUID для обновления пары |

## Этап 2: Использование access token

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant API as REST API
    participant Filter as Security Filter
    participant Decoder as JwtDecoder
    participant Converter as JwtAuthenticationConverter

    Client->>API: GET /api/v1.0.0/workshops/topology<br/>Authorization: Bearer <accessToken>
    API->>Filter: Проверка токена
    Filter->>Decoder: decode(accessToken)
    Decoder->>Decoder: Проверка подписи (HS256)
    Decoder->>Decoder: Проверка issuer (scada-mobile)
    Decoder->>Decoder: Проверка audience (scada-mobile-api)
    Decoder->>Decoder: Проверка expiry
    Decoder-->>Filter: Jwt
    Filter->>Converter: convert(Jwt)
    Converter->>Converter: Извлечь claim "role"
    Converter-->>Filter: JwtAuthenticationToken (ROLE_*)
    Filter-->>API: Продолжить обработку
    API-->>Client: 200 OK + данные
```

## Этап 3: Ротация токенов (refresh)

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant Auth as AuthController
    participant Service as AuthService
    participant TokenRepo as RefreshTokenRepository
    participant DB as БД

    Client->>Auth: POST /auth/refresh<br/>{refreshToken}
    Auth->>Service: rotate(refreshToken)
    Service->>Service: SHA-256(refreshToken)
    Service->>TokenRepo: findByTokenHash(hash)
    TokenRepo->>DB: SELECT * FROM refresh_tokens WHERE token_hash = ?
    DB-->>TokenRepo: RefreshTokenEntity
    TokenRepo-->>Service: Optional<RefreshTokenEntity>

    alt Токен не найден, истек или отозван
        Service-->>Auth: AuthenticationException
        Auth-->>Client: 401 Unauthorized
    else Токен валиден
        Service->>TokenRepo: revoke(oldTokenId)
        TokenRepo->>DB: UPDATE refresh_tokens SET revoked = true
        Service->>Service: generateAccessToken(userId, role)
        Service->>Service: generateRefreshToken()
        Service->>Service: SHA-256(newRefreshToken)
        Service->>TokenRepo: save(newHash, userId, expiresAt)
        TokenRepo->>DB: INSERT INTO refresh_tokens
        Service-->>Auth: AuthRefreshResponseDTO
        Auth-->>Client: 200 OK<br/>{accessToken, refreshToken}
    end
```

### Почему ротация важна

- При компрометации access token злоумышленник имеет доступ максимум 15 минут.
- При компрометации refresh token он будет отозван при следующем легитимном использовании.
- Каждое обновление генерирует новую пару — старая пара становится недействительной.

## Этап 4: Выход (logout)

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant Auth as AuthController
    participant Service as AuthService
    participant TokenRepo as RefreshTokenRepository
    participant DB as БД

    Client->>Auth: POST /auth/logout<br/>{refreshToken}
    Auth->>Service: revoke(refreshToken)
    Service->>Service: SHA-256(refreshToken)
    Service->>TokenRepo: revokeByHash(hash)
    TokenRepo->>DB: UPDATE refresh_tokens SET revoked = true
    TokenRepo-->>Service: OK
    Service-->>Auth: OK
    Auth-->>Client: 200 OK
    Client->>Client: Удалить токены из localStorage
```

## Этап 5: WebSocket аутентификация

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant Ws as WebSocket Endpoint
    participant Interceptor as WebSocketJwtInterceptor
    participant JwtProvider as JwtTokenProvider

    Client->>Ws: GET /ws/live?token=<accessToken>
    Ws->>Interceptor: beforeHandshake(request, response, handler, attributes)
    Interceptor->>Interceptor: Извлечь token из query param
    Interceptor->>JwtProvider: validateToken(token)
    JwtProvider-->>Interceptor: userId (или исключение)

    alt Токен невалиден
        Interceptor-->>Ws: false (отклонить handshake)
        Ws-->>Client: 401 Unauthorized
    else Токен валиден
        Interceptor->>Interceptor: attributes.put("userId", userId)
        Interceptor-->>Ws: true (продолжить handshake)
        Ws-->>Client: 101 Switching Protocols
    end
```

## Этап 6: Доступ к админ-панели

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant Admin as AdminUserController
    participant Security as Spring Security

    Client->>Admin: GET /api/v1.0.0/admin/users/list<br/>Authorization: Bearer <token>
    Admin->>Security: @PreAuthorize("hasRole('ADMIN')")
    Security->>Security: Извлечь роль из JWT

    alt Роль != ADMIN
        Security-->>Admin: AccessDeniedException
        Admin-->>Client: 403 Forbidden
    else Роль == ADMIN
        Security-->>Admin: Продолжить
        Admin-->>Client: 200 OK + список пользователей
    end
```

## Сравнение токенов

| Характеристика | Access Token | Refresh Token |
|----------------|--------------|---------------|
| Формат | JWT (HS256) | UUID (plain text) |
| Срок действия | 15 минут | 7 дней |
| Хранение на клиенте | localStorage | localStorage |
| Хранение на сервере | Не хранится | SHA-256 хеш в БД |
| Назначение | Доступ к API | Получение новой пары |
| Передача | Header `Authorization: Bearer` | Body JSON |
| Отзыв | Невозможен (stateless) | Возможен (флаг `revoked`) |
| Ротация | При каждом refresh | При каждом refresh |
