# SCADA Mobile: пошаговый запуск в PROD через консоль

`Примечание` Должен быть установлен Docker и Docker Compose.

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

Откройте `.env.prod.local` и задайте минимум:

1. `BACKEND_PORT`
2. `FRONTEND_PORT`
3. `CORS_POLICY_ALLOWED_ORIGINS`
4. Переменные по аппаратам: `PRINTSRV_<ID>_HOST` и `PRINTSRV_<ID>_PORT`

Критично:

1. `CORS_POLICY_ALLOWED_ORIGINS` должен совпадать с точным origin фронта в адресной строке браузера.
2. Если фронт открывают по IP/домену сервера, нельзя оставлять только `http://localhost:5500`.
3. Для нескольких вариантов доступа перечисляйте origin через запятую без пробелов. Пример: `http://localhost:5500,http://999.9.9.9:9999`

Пример:

~~~env
BACKEND_PORT=9999
FRONTEND_PORT=9998
CORS_POLICY_ALLOWED_ORIGINS=http://999.9.9.9:9998

PRINTSRV_TREPKO1_HOST=999.9.9.9
PRINTSRV_TREPKO1_PORT=9999
PRINTSRV_TREPKO2_HOST=999.9.9.9
PRINTSRV_TREPKO2_PORT=9999
PRINTSRV_HASSIA1_HOST=999.9.9.9
PRINTSRV_HASSIA1_PORT=9999
PRINTSRV_HASSIA2_HOST=999.9.9.9
PRINTSRV_HASSIA2_PORT=9999
PRINTSRV_HASSIA4_HOST=999.9.9.9
PRINTSRV_HASSIA4_PORT=9999
PRINTSRV_HASSIA5_HOST=999.9.9.9
PRINTSRV_HASSIA5_PORT=9999
PRINTSRV_HASSIA6_HOST=999.9.9.9
PRINTSRV_HASSIA6_PORT=9999
PRINTSRV_GRUNWALD1_HOST=999.9.9.9
PRINTSRV_GRUNWALD1_PORT=9999
PRINTSRV_GRUNWALD2_HOST=999.9.9.9
PRINTSRV_GRUNWALD2_PORT=9999
PRINTSRV_HASSIA3_HOST=999.9.9.9
PRINTSRV_HASSIA3_PORT=9999
PRINTSRV_BOSCH_HOST=999.9.9.9
PRINTSRV_BOSCH_PORT=9999
PRINTSRV_GRUNWALD5_HOST=999.9.9.9
PRINTSRV_GRUNWALD5_PORT=9999
PRINTSRV_GRUNWALD8_HOST=999.9.9.9
PRINTSRV_GRUNWALD8_PORT=9999
PRINTSRV_GRUNWALD11_HOST=999.9.9.9
PRINTSRV_GRUNWALD11_PORT=9999
~~~

### Пилотная проверка только одного аппарата (Hassia 4)

Если нужно проверить только один аппарат, достаточно явно задать
только `PRINTSRV_HASSIA4_HOST` и `PRINTSRV_HASSIA4_PORT`. Остальные просто не указывать (в работающем фронте они должны отображаться как «Нет данных»).

Пример минимального `.env.prod.local`:

~~~env
BACKEND_PORT=9999
FRONTEND_PORT=9998
CORS_POLICY_ALLOWED_ORIGINS=http://999.9.9.9:9998

# Единственный аппарат для проверки
PRINTSRV_HASSIA4_HOST=999.9.9.9
PRINTSRV_HASSIA4_PORT=9999
~~~

Где:

1. `999.9.9.9` — плейсхолдер IP машины Hassia 4; в `.env.prod.local` указывается фактическое значение.
2. `9999` — плейсхолдер TCP-порта PrintSrv; в `.env.prod.local` указывается фактическое значение.

Остальные аппараты можно временно не заполнять: backend запустится,
а не подключенные аппараты будут отображаться как «Нет данных».

## 3) Запуск PROD-стека

~~~bash
make docker-prod-up
~~~

После запуска фронт будет доступен по ссылке: `http://999.9.9.9:9998` (или по порту, который вы указали в `FRONTEND_PORT`).

Проверка статуса контейнеров:

~~~bash
make docker-ps
~~~

Проверка здоровья backend:

~~~bash
curl http://localhost:8080/api/v1.0.0/health/live
~~~

Если изменили `BACKEND_PORT`, используйте его в URL.

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
2. `403` — origin не входит в `CORS_POLICY_ALLOWED_ORIGINS`.

## 5) Остановка PROD-стека

~~~bash
make docker-prod-down
~~~

## 6) Если что-то не стартовало

1. Проверьте, что порты не заняты: `BACKEND_PORT` и `FRONTEND_PORT`.
2. Проверьте заполнение нужных `PRINTSRV_<ID>_HOST` и `PRINTSRV_<ID>_PORT`.
3. Пересоберите без кеша:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml build --no-cache
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml up -d
~~~
