# Docker Deploy (SCADA Mobile)

Короткая инструкция для локального запуска и подготовки к деплою на Linux-сервер/Kubernetes.

## 1) Docker в dev режиме

Из корня проекта:

~~~bash
make docker-dev-up
~~~

Проверка:

~~~bash
make docker-ps
~~~

Открыть:

- Frontend: http://localhost:5500
- Backend health: http://localhost:8080/api/v1.0.0/health/live

Остановка:

~~~bash
make docker-dev-down
~~~

Примечание для мобильной проверки через ngrok:

- dev-профиль backend допускает origin-паттерны `https://*.ngrok-free.dev` и `https://*.ngrok-free.app`;
- отдельный CORS env для dev не нужен;
- используйте `ngrok http 5500` и открывайте HTTPS URL туннеля.

## 2) Что уже учтено в Docker-конфигах

- фиксированные версии базовых образов (без latest)
- multi-stage сборка для backend и frontend
- минимальные runtime-образы
- отдельные .dockerignore для backend/frontend + root .dockerignore
- healthcheck у обоих сервисов
- явные команды запуска контейнеров
- фронт в контейнере проксирует /api и /ws в backend (same-origin для браузера)

## 3) Docker в prod режиме (локальная репетиция и CI)

1. Скопируйте шаблон env-файла и отредактируйте значения:

~~~bash
cp .env.prod.example .env.prod.local
~~~

2. Проверьте, что минимум настроены:

- BACKEND_PORT=нужный_порт_бекенда
- FRONTEND_PORT=нужный_порт_фронтенда
- CORS_POLICY_ALLOWED_ORIGINS=https://ваш-домен-фронта
- PRINTSRV_<ID>_HOST / PRINTSRV_<ID>_PORT (например, PRINTSRV_HASSIA4_HOST / PRINTSRV_HASSIA4_PORT)

Важно по CORS/WebSocket:

- `CORS_POLICY_ALLOWED_ORIGINS` должен содержать точный origin фронта, под которым его реально открывают в браузере.
- Если UI открывают как `http://999.9.9.9:9999`, значение должно включать именно `http://999.9.9.9:9999`, а не только `http://localhost:5500`.
- Это значение используется не только для REST CORS, но и для WebSocket handshake на `/ws/live` и `/ws/unit/*`.
- Если нужны и локальный запуск, и доступ по IP/домену, перечислите несколько origin через запятую без пробелов. Пример: `CORS_POLICY_ALLOWED_ORIGINS=http://localhost:5500,http://999.9.9.9:9999`

3. Поднимите prod-режим:

~~~bash
make docker-prod-up
~~~

Важно: в prod-режиме порты BACKEND_PORT и FRONTEND_PORT обязательны. Если их нет в env, docker compose завершится с ошибкой (fail-fast).
Также в prod-профиле хосты и порты PrintSrv обязательны; без них backend не сможет корректно инициализировать все инстансы.

Остановка:

~~~bash
make docker-prod-down
~~~

## 4) Публикация образов в реестр

~~~bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod.local build
docker tag scada-mobile/backend:0.1.0 registry.company.local/scada-mobile/backend:0.1.0
docker tag scada-mobile/frontend:0.1.0 registry.company.local/scada-mobile/frontend:0.1.0
docker push registry.company.local/scada-mobile/backend:0.1.0
docker push registry.company.local/scada-mobile/frontend:0.1.0
~~~

Далее эти образы можно использовать в Kubernetes Deployment манифестах.
