# Нагрузочное тестирование (load-tests)

Методика и артефакты нагрузочного тестирования системы (эпик #51).
Этап 1 — локально на машине разработчика; этап 2 — повтор на сервере с теми же
скриптами и командами (всё параметризовано через env).

**Никогда не грузим prod.** Только изолированный стенд.

## Стенд (НТ-1, #63)

Изолированное окружение, не пересекающееся с dev-окружением:

| Компонент | Dev | Load-стенд |
|---|---|---|
| postgres | `localhost:5432` (контейнер `postgres`) | `localhost:5433` (контейнер `scada-loadtest-postgres`) |
| backend | `:8080`, профиль `dev` | `:8081`, профиль `loadtest` |
| PrintSrv | mock (in-process) | тот же mock (профиль `loadtest`) |

Профиль `loadtest` = prod-like поведение (WARN-логи, JSON-лог через
AsyncAppender, без Swagger) + mock PrintSrv. Это даёт честные замеры без
DEBUG-шума.

Данные в стенд-БД (идемпотентные сиды, можно переприменять):

- `scripts/seed_notifications.sql` — роли, 2 dev-пользователя, топология
  (2 цеха, 14 автоматов, устройства)
- `scripts/seed_loadtest_users.sql` — 500 нагрузочных пользователей
  (`code` = `20001`..`20500`, пароль `password`, роль Master,
  `password_temporary=false`), назначения на автоматы и настройки уведомлений

## Команды (Makefile)

```bash
make load-up            # поднять стенд целиком:
                        #   postgres → backend (Flyway-миграции) → сид → рестарт backend
                        #   (рестарт нужен: polling-воркеры читают топологию при старте)
make load-down          # остановить backend и удалить контейнер postgres (volume остаётся)
make load-db-reset      # удалить контейнер И volume (чистый лист), потом make load-up
make load-db-seed       # переприменить сиды вручную
make load-db-backup     # pg_dump в load-tests/backups/ — ОБЯЗАТЕЛЬНО перед прогонами
make load-db-restore FILE=load-tests/backups/loadtest-YYYYMMDD-HHMMSS.sql
make load-back-logs     # лог backend стенда (backend/.backend-loadtest.log)
```

Все переменные переопределяются снаружи (`LOAD_DB_PORT`, `LOAD_BACKEND_PORT`,
`LOAD_DB_NAME`, …) — на сервере те же команды работают с другими портами/хостами.
JWT-секреты берутся из `backend/.env.dev` (генерируются автоматически, как в `make back-run`).

Типовой цикл прогона:

```bash
make load-up            # 1. поднять стенд
make load-db-backup     # 2. бэкап перед прогоном
# k6 run ...            # 3. прогон (скрипты — НТ-2, #69)
make load-db-restore FILE=...   # 4. при необходимости откатить БД
```

## Проверка стенда после поднятия

```bash
# Логин нагрузочным пользователем (любой код 20001..20500):
curl -X POST http://localhost:8081/api/v1.0.0/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"workerCode":"20001","password":"password"}'

# Топология (с accessToken из ответа логина):
curl http://localhost:8081/api/v1.0.0/workshops/topology \
  -H "Authorization: Bearer <accessToken>"

# WebSocket: ws://localhost:8081/ws/live?token=<accessToken>
#   клиент→сервер: {"action":"SUBSCRIBE_WORKSHOP","workshopId":1}
#   сервер→клиент: ALERT_SNAPSHOT, NOTIFICATION_SNAPSHOT, UNITS_STATUS, ALERT
```

## k6-скрипты (НТ-2, #69)

Каталог `load-tests/k6/`:

- `lib/common.js` — env-конфиг (`BASE_URL`, `WS_URL`, диапазон пользователей,
  instance IDs автоматов, `STAGES`, `WS_SESSION_MS`) + хелпер логина
- `auth-login.js` — массовый login (помним: BCrypt CPU-bound, threshold мягче)
- `rest-topology.js` — GET /workshops/topology с ETag (200 → 304)
- `ws-live.js` — WS /ws/live: SUBSCRIBE_WORKSHOP, снапшоты, UNITS_STATUS
- `ws-unit.js` — WS /ws/unit/{instanceId}: пакет из 4 сообщений + push.
  **instanceId — строка** (`trepko1`, ...), не числовой unit_id

Запуск (summary всегда падает в `load-tests/results/`):

```bash
make load-k6 SCRIPT=load-tests/k6/ws-live.js                 # дефолтный профиль
make load-k6 SCRIPT=load-tests/k6/ws-live.js BASE_URL=http://server:8081   # сервер
make load-k6 SCRIPT=load-tests/k6/auth-login.js 'STAGES=[{"duration":"1m","target":50}]'
```

Дефолтные thresholds зашиты в скриптах (из критериев приёмки эпика):
REST p95 < 200 мс; WS snapshot p95 < 500 мс; WS ошибки < 0.1%.

## Этап 2 (сервер)

На сервере: те же `make load-*` команды (Linux, POSIX-ветка Makefile) либо
`SPRING_PROFILES_ACTIVE=loadtest` + env-переопределения `SCADA_MOBILE_DATABASE_*`.
k6-скрипты параметризованы через `BASE_URL`/`API_URL` (НТ-2, #69) — перепрогон
без изменения кода.

## Артефакты (не коммитятся)

- `load-tests/backups/` — дампы БД
- `load-tests/results/` — результаты прогонов k6
