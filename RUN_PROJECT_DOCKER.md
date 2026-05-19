# SCADA Mobile: пошаговый запуск в PROD через консоль

`Примечание` Должен быть установлен Docker и Docker Compose.

## Purpose
Пошаговый запуск прод-стека через Docker Compose и базовая диагностика.

## Table of contents
- [Purpose](#purpose)
- [1) Клонировать репозиторий](#1-клонировать-репозиторий)
- [2) Подготовить PROD-конфигурацию](#2-подготовить-prod-конфигурацию)
- [3) Запуск PROD-стека](#3-запуск-prod-стека)
- [4) Логи и диагностика](#4-логи-и-диагностика)
- [5) Остановка PROD-стека](#5-остановка-prod-стека)
- [6) Если что-то не стартовало](#6-если-что-то-не-стартовало)

## 1) Клонировать репозиторий

~~~bash
git clone https://github.com/savushkin-dev/scada-mobile.git
cd scada-mobile
~~~

## 2) Подготовить PROD-конфигурацию

Создайте файл окружения из шаблона.

Windows PowerShell:

~~~powershell
Copy-Item .env.prod.example .env.prod.local
~~~

Linux/macOS:

~~~bash
cp .env.prod.example .env.prod.local
~~~

### Генерация JWT-секретов (обязательно перед первым запуском)

Все переменные окружения проекта имеют префикс `SCADA_MOBILE_` для совместимости с другими сервисами на одном сервере.

**bash / Linux / macOS:**

~~~bash
export SCADA_MOBILE_JWT_ACCESS_SECRET=$(openssl rand -base64 32)
export SCADA_MOBILE_JWT_REFRESH_SECRET=$(openssl rand -base64 32)
echo "ACCESS:  $SCADA_MOBILE_JWT_ACCESS_SECRET"
echo "REFRESH: $SCADA_MOBILE_JWT_REFRESH_SECRET"
~~~

**PowerShell:**

~~~powershell
$env:SCADA_MOBILE_JWT_ACCESS_SECRET = openssl rand -base64 32
$env:SCADA_MOBILE_JWT_REFRESH_SECRET = openssl rand -base64 32
Write-Host "ACCESS:  $env:SCADA_MOBILE_JWT_ACCESS_SECRET"
Write-Host "REFRESH: $env:SCADA_MOBILE_JWT_REFRESH_SECRET"
~~~

Скопируйте сгенерированные значения в `.env.prod.local`.

### Минимальный набор переменных

Откройте `.env.prod.local` и задайте минимум:

1. `SCADA_MOBILE_BACKEND_PORT`
2. `SCADA_MOBILE_FRONTEND_PORT`
3. `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS`
4. `SCADA_MOBILE_JWT_ACCESS_SECRET` и `SCADA_MOBILE_JWT_REFRESH_SECRET`
5. `SCADA_MOBILE_DATABASE_PASSWORD`
6. Переменные по автоматам: `SCADA_MOBILE_PRINTSRV_<ID>_HOST` и `SCADA_MOBILE_PRINTSRV_<ID>_PORT`

Критично:

1. `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS` должен совпадать с точным origin фронта в адресной строке браузера.
2. Если фронт открывают по IP/домену сервера, нельзя оставлять только `http://localhost:5500`.
3. Для нескольких вариантов доступа перечисляйте origin через запятую без пробелов. Пример: `http://localhost:5500,http://999.9.9.9:9999`

Пример:

~~~env
SCADA_MOBILE_BACKEND_PORT=9999
SCADA_MOBILE_FRONTEND_PORT=9998
SCADA_MOBILE_POSTGRES_PORT=5432
SCADA_MOBILE_DATABASE_PASSWORD=your_secure_password_here
SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS=http://999.9.9.9:9998

SCADA_MOBILE_JWT_ACCESS_SECRET=YOUR_GENERATED_ACCESS_SECRET
SCADA_MOBILE_JWT_REFRESH_SECRET=YOUR_GENERATED_REFRESH_SECRET

SCADA_MOBILE_PRINTSRV_TREPKO1_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_TREPKO1_PORT=9999
SCADA_MOBILE_PRINTSRV_TREPKO2_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_TREPKO2_PORT=9999
SCADA_MOBILE_PRINTSRV_HASSIA1_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA1_PORT=9999
SCADA_MOBILE_PRINTSRV_HASSIA2_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA2_PORT=9999
SCADA_MOBILE_PRINTSRV_HASSIA4_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA4_PORT=9999
SCADA_MOBILE_PRINTSRV_HASSIA5_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA5_PORT=9999
SCADA_MOBILE_PRINTSRV_HASSIA6_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA6_PORT=9999
SCADA_MOBILE_PRINTSRV_GRUNWALD1_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_GRUNWALD1_PORT=9999
SCADA_MOBILE_PRINTSRV_GRUNWALD2_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_GRUNWALD2_PORT=9999
SCADA_MOBILE_PRINTSRV_HASSIA3_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA3_PORT=9999
SCADA_MOBILE_PRINTSRV_BOSCH_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_BOSCH_PORT=9999
SCADA_MOBILE_PRINTSRV_GRUNWALD5_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_GRUNWALD5_PORT=9999
SCADA_MOBILE_PRINTSRV_GRUNWALD8_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_GRUNWALD8_PORT=9999
SCADA_MOBILE_PRINTSRV_GRUNWALD11_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_GRUNWALD11_PORT=9999
~~~

### Пилотная проверка только одного автомата (Hassia 4)

Если нужно проверить только один автомат, достаточно явно задать
только `SCADA_MOBILE_PRINTSRV_HASSIA4_HOST` и `SCADA_MOBILE_PRINTSRV_HASSIA4_PORT`. Остальные просто не указывать (в работающем фронте они должны отображаться как «Нет данных»).

Пример минимального `.env.prod.local`:

~~~env
SCADA_MOBILE_BACKEND_PORT=9999
SCADA_MOBILE_FRONTEND_PORT=9998
SCADA_MOBILE_DATABASE_PASSWORD=your_secure_password_here
SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS=http://999.9.9.9:9998

SCADA_MOBILE_JWT_ACCESS_SECRET=YOUR_GENERATED_ACCESS_SECRET
SCADA_MOBILE_JWT_REFRESH_SECRET=YOUR_GENERATED_REFRESH_SECRET

# Единственный автомат для проверки
SCADA_MOBILE_PRINTSRV_HASSIA4_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA4_PORT=9999
~~~

Где:

1. `999.9.9.9` — плейсхолдер IP машины Hassia 4; в `.env.prod.local` указывается фактическое значение.
2. `9999` — плейсхолдер TCP-порта PrintSrv; в `.env.prod.local` указывается фактическое значение.

Остальные автоматы можно временно не заполнять: backend запустится,
а не подключенные автоматы будут отображаться как «Нет данных».

## 3) Запуск PROD-стека

~~~bash
make docker-prod-up
~~~

После запуска фронт будет доступен по ссылке: `http://999.9.9.9:9998` (или по порту, который вы указали в `SCADA_MOBILE_FRONTEND_PORT`).

Проверка статуса контейнеров:

~~~bash
make docker-ps
~~~

Проверка здоровья backend:

~~~bash
curl http://localhost:8080/api/v1.0.0/health/live
~~~

Если изменили `SCADA_MOBILE_BACKEND_PORT`, используйте его в URL.

Проверка подключения к PostgreSQL:

~~~bash
docker exec -it scada-mobile-postgres pg_isready -U scada_user -d scada_mobile
~~~

## 4) Логи и диагностика

Логи всех сервисов:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs -f
~~~

Логи только backend:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
~~~

Логи только frontend:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs -f frontend
~~~

Логи только PostgreSQL:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs -f postgres
~~~

Быстрая проверка WebSocket origin-политики:

~~~bash
curl -i \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Origin: http://999.9.9.9:9999" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  http://999.9.9.9:9999/ws/live
~~~

Ожидаемое поведение:

1. `101 Switching Protocols` — origin разрешён, handshake проходит.
2. `403` — origin не входит в `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS`.

## 5) Остановка PROD-стека

~~~bash
make docker-prod-down
~~~

Данные PostgreSQL сохраняются в именованном Docker-томе `scada-mobile-postgres-data` и не теряются при остановке контейнеров.

## 6) Если что-то не стартовало

1. Проверьте, что порты не заняты: `SCADA_MOBILE_BACKEND_PORT`, `SCADA_MOBILE_FRONTEND_PORT`, `SCADA_MOBILE_POSTGRES_PORT`.
2. Проверьте заполнение нужных `SCADA_MOBILE_PRINTSRV_<ID>_HOST` и `SCADA_MOBILE_PRINTSRV_<ID>_PORT`.
3. Проверьте, что заданы `SCADA_MOBILE_JWT_ACCESS_SECRET` и `SCADA_MOBILE_JWT_REFRESH_SECRET`.
4. Проверьте, что задан `SCADA_MOBILE_DATABASE_PASSWORD`.
5. Пересоберите без кеша:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml build --no-cache
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml up -d
~~~
