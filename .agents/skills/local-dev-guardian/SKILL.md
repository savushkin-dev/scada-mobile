---
name: local-dev-guardian
description: 'Local development workflow skill for this SCADA Mobile project. Use when: starting backend/frontend, restarting services after config changes, debugging CORS/JWT/auth issues, or verifying changes via Playwright. Covers Windows-specific process management, Gradle bootRun, Vite dev server, and cross-origin testing.'
argument-hint: 'Опиши задачу локальной разработки (запуск, рестарт, отладка)'
user-invocable: true
disable-model-invocation: false
---

# Local Development Guardian

## Purpose
- Standardize local dev startup, restart, and debugging workflow.
- Prevent time waste on repeated CORS/JWT/process issues.

## When to Use
- Starting backend or frontend for the first time in a session.
- Restarting backend after Java/config changes.
- Debugging CORS preflight failures, 401 auth errors, or 500 server errors.
- Testing admin panel or API changes via Playwright.

## Project Layout
- **Backend:** `backend/` — Spring Boot 4, Gradle, port `8080`.
- **Frontend:** `frontend/` — React 18 + Vite, port `5500`.
- **DB:** PostgreSQL in Docker (started separately or via `make docker-dev-up`).

## Mandatory Startup Sequence

### 1. Ensure DB is running
```bash
make docker-dev-up   # или проверь docker ps
```

### 2. Start Backend
**Важно:** `make back-run` использует PowerShell синтаксис и НЕ работает в bash. Запускай напрямую через Gradle:

```bash
export JWT_ACCESS_SECRET="$(openssl rand -base64 32)"
export JWT_REFRESH_SECRET="$(openssl rand -base64 32)"
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

Или в background:
```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev' > ../backend.log 2>&1 &
echo $! > ../.backend.pid
```

**JWT-секреты обязательны.** Без них бэкенд падает на старте с:
> `JWT_ACCESS_SECRET env var is not set. Generate with: openssl rand -base64 32`

### 3. Start Frontend
```bash
cd frontend && npm run dev   # Vite на порту 5500
```

Или через Makefile:
```bash
make front-dev
```

## Restart Rules

### Java-код изменён (entities, controllers, services)
Spring Boot DevTools обычно подхватывает автоматически. Если нет:
```bash
# Найди и убей процесс на порту 8080
PID=$(netstat -ano | grep ':8080' | grep LISTENING | awk '{print $5}')
powershell.exe -Command "Stop-Process -Id $PID -Force"
# Перезапусти с новыми JWT-секретами (см. выше)
```

### YAML-конфиг изменён (CORS, DB, JWT)
DevTools **НЕ** всегда перезагружает YAML. Лучше полный рестарт:
```bash
# Убить + запустить заново (см. раздел "Start Backend")
```

### Фронтенд TypeScript изменён
Vite HMR подхватывает автоматически. Если что-то сломалось:
```bash
# Перезапусти Vite dev server
```

## CORS Checklist

CORS настроен через `CorsFilterConfig` (servlet filter, `HIGHEST_PRECEDENCE`).
Конфигурация читается из `application.yaml` → `CorsProperties`.

**Если preflight возвращает 403 или неправильные заголовки:**
1. Проверь `application.yaml` — именно там задаются `allowed-methods`, `allowed-headers`, `exposed-headers`.
2. Не полагайся только на дефолты в `CorsProperties.java` — YAML переопределяет их.
3. После изменения YAML нужен **полный рестарт бэкенда** (DevTools не всегда подхватывает).

**Проверка CORS через curl:**
```bash
curl -s -X OPTIONS \
  -H "Origin: http://localhost:5500" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization, Range" \
  http://localhost:8080/api/v1.0.0/admin/roles -v
```

Ожидаемый ответ:
- `Access-Control-Allow-Origin: http://localhost:5500`
- `Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`
- `Access-Control-Allow-Headers: Authorization, Range` (или что задано в YAML)
- `Access-Control-Expose-Headers: Content-Range, ETag`

## JWT / Auth Checklist

**После каждого рестарта бэкенда JWT-секреты меняются.** Все старые токены становятся невалидными.

**Быстро получить новый токен:**
```bash
curl -s -X POST http://localhost:8080/api/v1.0.0/auth/login \
  -H "Content-Type: application/json" \
  -d '{"workerCode":"ADM-000001","password":"password"}'
```

**Установить токен в браузер (Playwright):**
```js
localStorage.setItem('scada.accessToken', '<accessToken>');
localStorage.setItem('scada.refreshToken', '<refreshToken>');
localStorage.setItem('scada.role', 'ADMIN');
localStorage.setItem('scada.userId', '3');
```

## Admin Panel Testing via Playwright

**Авторизация:**
1. Логин через `/login` с `ADM-000001` / `password`, ИЛИ
2. Прямая установка токенов в `localStorage` (быстрее).

**Проверка FK-полей:**
- React Admin `ReferenceField source="unitId"` ожидает поле `unitId` в JSON-ответе.
- JPA `@ManyToOne` сериализует связь как вложенный объект (`unit: {id: 1}`), а не как `unitId`.
- Добавь getter `getUnitId()` в entity, чтобы Jackson сериализовал его.

**Проверка write-операций:**
- `ra-data-simple-rest` ожидает `Content-Range` заголовок для пагинации.
- DELETE возвращает пустое тело — `dataProvider.httpClient` должен обрабатывать `response.text()` → `JSON.parse` только если не пусто.

## Windows Process Management

```bash
# Найти PID процесса на порту
netstat -ano | grep ':8080' | grep LISTENING

# Убить процесс
powershell.exe -Command "Stop-Process -Id <PID> -Force"

# Список Java-процессов
ps -W | grep java
```

## Common Errors & Fixes

| Ошибка | Причина | Решение |
|--------|---------|---------|
| `JWT_ACCESS_SECRET env var is not set` | Не заданы секреты | `export JWT_ACCESS_SECRET=...` перед запуском |
| `Invalid CORS request` (403) | Метод/заголовок не в `allowed-methods`/`allowed-headers` | Добавить в `application.yaml`, рестарт |
| `Content-Range header is missing` | Бэкенд не возвращает заголовок | Добавить в `AdminReadController.pageResponse()` и `application.yaml` exposed-headers |
| `Sort expression must only contain property references` | React Admin шлёт `sort=["id","ASC"]` | Кастомный `getList` в `dataProvider.ts` — преобразовать в `sort=id,asc&page=0&size=10` |
| `Unexpected end of JSON input` на DELETE | Пустой ответ (204) парсится как JSON | `httpClient` должен проверять `text` перед `JSON.parse` |
| FK-поля пустые в админке | Нет `unitId`/`roleId` в JSON | Добавить `getUnitId()`/`getRoleId()` getter'ы в entity |
