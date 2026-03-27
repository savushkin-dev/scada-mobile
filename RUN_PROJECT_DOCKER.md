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

Пример:

~~~env
BACKEND_PORT=8080
FRONTEND_PORT=5500
CORS_POLICY_ALLOWED_ORIGINS=http://localhost:5500

PRINTSRV_TREPKO1_HOST=127.0.0.1
PRINTSRV_TREPKO1_PORT=5561
PRINTSRV_TREPKO2_HOST=127.0.0.1
PRINTSRV_TREPKO2_PORT=5562
PRINTSRV_HASSIA1_HOST=127.0.0.1
PRINTSRV_HASSIA1_PORT=5563
PRINTSRV_HASSIA2_HOST=127.0.0.1
PRINTSRV_HASSIA2_PORT=5564
PRINTSRV_HASSIA4_HOST=127.0.0.1
PRINTSRV_HASSIA4_PORT=5565
PRINTSRV_HASSIA5_HOST=127.0.0.1
PRINTSRV_HASSIA5_PORT=5566
PRINTSRV_HASSIA6_HOST=127.0.0.1
PRINTSRV_HASSIA6_PORT=5567
PRINTSRV_GRUNWALD1_HOST=127.0.0.1
PRINTSRV_GRUNWALD1_PORT=5571
PRINTSRV_GRUNWALD2_HOST=127.0.0.1
PRINTSRV_GRUNWALD2_PORT=5572
PRINTSRV_HASSIA3_HOST=127.0.0.1
PRINTSRV_HASSIA3_PORT=5573
PRINTSRV_BOSCH_HOST=127.0.0.1
PRINTSRV_BOSCH_PORT=5574
PRINTSRV_GRUNWALD5_HOST=127.0.0.1
PRINTSRV_GRUNWALD5_PORT=5575
PRINTSRV_GRUNWALD8_HOST=127.0.0.1
PRINTSRV_GRUNWALD8_PORT=5576
PRINTSRV_GRUNWALD11_HOST=127.0.0.1
PRINTSRV_GRUNWALD11_PORT=5577
~~~

### Пилотная проверка только одного аппарата (Hassia 4)

Если нужно проверить только один аппарат, достаточно явно задать
только `PRINTSRV_HASSIA4_HOST` и `PRINTSRV_HASSIA4_PORT`. Остальные просто не указывать (в работающем фронте они должны отображаться как «Нет данных»).

Пример минимального `.env.prod.local`:

~~~env
BACKEND_PORT=8080
FRONTEND_PORT=5500
CORS_POLICY_ALLOWED_ORIGINS=http://localhost:5500

# Единственный аппарат для проверки
PRINTSRV_HASSIA4_HOST=192.168.10.45
PRINTSRV_HASSIA4_PORT=5565
~~~

Где:

1. `192.168.10.45` — реальный IP машины Hassia 4 в сети предприятия.
2. `5565` — TCP-порт PrintSrv на этой машине.

Если PrintSrv запущен на той же машине, где работает Docker (локальный пилот),
для backend-контейнера используйте `PRINTSRV_HASSIA4_HOST=host.docker.internal`,
а не `localhost`.

Остальные аппараты можно временно не заполнять: backend запустится,
а не подключенные аппараты будут отображаться как «Нет данных».

## 3) Запуск PROD-стека

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml up --build -d
~~~

После запуска фронт будет доступен по ссылке: `http://localhost:5500` (или по порту, который вы указали в `FRONTEND_PORT`).

Проверка статуса контейнеров:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml ps
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

## 5) Остановка PROD-стека

~~~bash
docker compose down
~~~

## 6) Если что-то не стартовало

1. Проверьте, что порты не заняты: `BACKEND_PORT` и `FRONTEND_PORT`.
2. Проверьте заполнение нужных `PRINTSRV_<ID>_HOST` и `PRINTSRV_<ID>_PORT`.
3. Пересоберите без кеша:

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml build --no-cache
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml up -d
~~~
