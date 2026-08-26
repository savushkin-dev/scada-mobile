# SCADA Machine API — интеграция автоматов (СКАДА)

## Purpose
Документ для разработчиков СКАДА: как программе, работающей на автомате, установить/снять флаг «последняя партия» и получать сообщения о его состоянии.

Состояние «последняя партия» хранится **перманентно на сервере** (PostgreSQL, таблица `production_notifications`) и является единым источником истины: его читают и фронтенд, и СКАДА. Состояние переживает рестарт backend.

## Table of contents
- [Purpose](#purpose)
- [Общая концепция](#общая-концепция)
- [Получение machine-токена](#получение-machine-токена)
- [Формат machine-JWT](#формат-machine-jwt)
- [REST: установка / снятие флага](#rest-установка--снятие-флага)
- [REST: чтение состояния](#rest-чтение-состояния)
- [WebSocket: приём сообщений](#websocket-приём-сообщений)
- [Отзыв токена](#отзыв-токена)
- [Коды ошибок](#коды-ошибок)

## Общая концепция

Автомат работает с тем же API, что и фронтенд, но аутентифицируется не логином/паролем работника, а **machine-JWT** — долгоживущим токеном с claim `subject_type = "machine"`, где `sub` — PrintSrv instance id автомата (например, `hassia1`).

Ограничение безопасности: автомат может управлять и читать состояние **только собственного аппарата** — `sub` токена обязан совпадать с аппаратом из пути запроса, иначе `403`.

Toggle-семантика: один и тот же субъект повторным вызовом снимает свой флаг. Снять флаг, установленный другим субъектом (работником или другим автоматом), нельзя — `409 already_active`.

## Получение machine-токена

Токен выпускает администратор через админ-API (роль `ADMIN`):

```
POST /api/v1.0.0/admin/machine-tokens
Authorization: Bearer <admin JWT>
Content-Type: application/json

{ "unitId": 12, "ttlDays": 365 }
```

Ответ `201`:

```json
{
  "token": "eyJhbGciOi...",
  "jti": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "unitId": 12,
  "printsrvInstanceId": "hassia1",
  "expiresAt": "2027-08-26T10:15:30"
}
```

- `token` возвращается **один раз** — СКАДА сохраняет его в защищённом хранилище автомата.
- `ttlDays` опционален; дефолт — `jwt.machine-token-expiration-days` (365 дней).
- Реестр выданных токенов (без значений): `GET /api/v1.0.0/admin/machine-tokens`.

## Формат machine-JWT

| Claim | Значение |
| --- | --- |
| `sub` | PrintSrv instance id автомата (`hassia1`) |
| `subject_type` | `machine` |
| `role` | `MACHINE` |
| `jti` | Уникальный id токена (для отзыва) |
| `iss` / `aud` | `scada-mobile` / `scada-mobile-api` |
| `exp` | Время истечения |

## REST: установка / снятие флага

```
POST /api/v1.0.0/line/{unitId}/last-batch
Authorization: Bearer <machine JWT>
```

`{unitId}` — числовой id автомата или его PrintSrv instance id; для machine-JWT обязан соответствовать `sub` токена. Тело запроса отсутствует.

Ответ `200` — флаг установлен:

```json
{ "status": "activated", "unitId": "12", "creatorId": "hassia1", "timestamp": "2026-08-26T10:20:30" }
```

Повторный вызов тем же токеном — снятие флага:

```json
{ "status": "deactivated", "unitId": "12", "creatorId": null, "timestamp": null }
```

Если флаг установлен другим субъектом — `409`:

```json
{ "status": "already_active", "unitId": "12", "creatorId": "42", "timestamp": null }
```

## REST: чтение состояния

```
GET /api/v1.0.0/line/{unitId}/last-batch
Authorization: Bearer <machine JWT>
```

Ответ `200`:

```json
{
  "unitId": "12",
  "printsrvInstanceId": "hassia1",
  "active": true,
  "creatorType": "MACHINE",
  "creatorId": "hassia1",
  "activatedAt": "2026-08-26T10:20:30"
}
```

При снятом флаге: `"active": false`, остальные nullable-поля — `null`.

## WebSocket: приём сообщений

Для отображения состояния на экране автомата СКАДА держит WS-соединение:

```
ws://<host>/ws/live?token=<machine JWT>
```

Handshake отклоняется с `401`, если токен отсутствует, невалиден, отозван или истёк.

Сразу после подключения сервер отправляет `NOTIFICATION_SNAPSHOT` — активные уведомления по собственному аппарату автомата (для machine-сессий список отфильтрован по `sub` токена):

```json
{
  "type": "NOTIFICATION_SNAPSHOT",
  "payload": [
    {
      "type": "NOTIFICATION",
      "unitId": "hassia1",
      "unitName": "Hassia №1",
      "creatorId": "hassia1",
      "creatorName": "СКАДА",
      "active": true,
      "timestamp": "2026-08-26T10:20:30"
    }
  ]
}
```

Далее при каждом toggle собственного аппарата (неважно, кем — работником с фронтенда или самим автоматом) приходит дельта `NOTIFICATION` с `active: true/false` — по ней экран автомата обновляет индикатор.

Machine-сессия также получает общие сообщения канала (`ALERT_SNAPSHOT`, `ALERT`) — их можно игнорировать, если на экране нужен только статус «последней партии».

Рекомендация: при обрыве соединения переподключаться с экспоненциальной задержкой; после переподключения актуальное состояние придёт в `NOTIFICATION_SNAPSHOT` (альтернатива — периодический polling `GET .../last-batch`).

## Отзыв токена

Администратор отзывает токен по `jti` из реестра:

```
DELETE /api/v1.0.0/admin/machine-tokens/{jti}
Authorization: Bearer <admin JWT>
```

Отозванный (или истёкший) токен отклоняется с `401` на каждом запросе — HTTP и WebSocket.

## Коды ошибок

| Код | Причина |
| --- | --- |
| `200` | Операция выполнена (`activated` / `deactivated`) |
| `401` | Токен отсутствует, невалиден, истёк или отозван |
| `403` | Machine-JWT обращается к чужому аппарату; у работника нет закрепления за аппаратом |
| `404` | Аппарат не найден |
| `409` | Флаг уже установлен другим субъектом (`already_active`) — снять может только создатель |
