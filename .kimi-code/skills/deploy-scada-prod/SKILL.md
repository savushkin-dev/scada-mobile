---
name: deploy-scada-prod
description: Use when пользователь просит залить, обновить или задеплоить SCADA Mobile на прод (реальный сервер, продакшен), упоминает «прод», «реальный сервер», «обновить сервер», ssh work или деплой через SSH MCP
---

# Деплой SCADA Mobile на прод

## Overview

Прод-сервер доступен только через SSH MCP, хост-алиас `work`. Обновление = строго фиксированная последовательность команд в папке проекта. Отклоняться от неё нельзя.

## ЖЁСТКИЕ ЗАПРЕТЫ (нарушение = немедленный стоп и отчёт)

- **Трогать можно ТОЛЬКО папку `~/scada-mobile`** на хосте `work`. Никаких других папок, файлов, сервисов, контейнеров (на сервере есть чужие: erp-servermethods, kafka, redis, foodpack и др. — НЕ ТРОГАТЬ).
- Никаких других хостов. Алиасы `scada_mobile` (10.35.0.4) и `scadadev2` существуют, но `scada_mobile` недоступен (timeout), `scadadev2` — только jump-хост. Прод = `work`, точка.
- **Не читать и не выводить содержимое `.env.prod.local`** (секреты). Проверять только его существование (`test -f`).
- Никаких экспериментов «по пути»: никаких `docker system prune`, `rm`, правок конфигов, миграций руками. Только команды из раздела ниже + read-only проверки.

## Сервер

| Параметр | Значение |
|----------|----------|
| SSH MCP alias | `work` (10.30.0.5, user vvk) |
| Папка проекта | `/home/vvk/scada-mobile` |
| Ветка | `main`, remote `origin` = GitHub savushkin-dev/scada-mobile |
| Env-файл | `.env.prod.local` (обязателен для prod-стека) |
| Контейнеры | `scada-mobile-backend-1`, `scada-mobile-frontend-1`, `scada-mobile-postgres` |

## Процедура обновления прода

Выполнять через `mcp__ssh__runRemoteCommand` (hostAlias `work`). Каждый шаг — отдельный вызов.

1. **Предпроверки (read-only):**
   - `cd ~/scada-mobile && git branch --show-current && git status -sb | head -3` — должно быть `main` и чистое дерево (нет ` M ` файлов). Если дерево грязное или ветка не main — стоп, отчитаться пользователю, не «чинить» самому.
   - `test -f ~/scada-mobile/.env.prod.local && echo ENV_OK`
2. **git pull:** `cd ~/scada-mobile && git pull --ff-only origin main 2>&1 | tail -5 && git log --oneline -1`. Не fast-forward → стоп, отчитаться.
3. **Стоп стек:** `cd ~/scada-mobile && make docker-prod-down 2>&1 | tail -5` — это `docker compose stop` (~12 c): контейнеры останавливаются, но НЕ удаляются. Проверка: `docker ps -a --filter name=scada-mobile` — статусы `Exited` (контейнеры на месте).
   **НЕ применять** `make docker-prod-recreate` в обычном обновлении — она полностью удаляет контейнеры (`docker compose down`, образы и данные сохраняются). Она нужна только «на всякий случай» для полного пересоздания.
4. **Старт стека:** `make docker-prod-up` = сборка образов + `up -d --build` и **длится дольше 60-секундного таймаута MCP**. Запускать только отсоединённо:
   `cd ~/scada-mobile && nohup make docker-prod-up > /tmp/scada-deploy.log 2>&1 & echo "pid $!"`
5. **Поллинг** (каждые ~45 c, не чаще): `docker ps --filter name=scada-mobile --format '{{.Names}} {{.Status}}'` и `tail -5 /tmp/scada-deploy.log`. Ждать, пока все 3 контейнера не станут `Up ... (healthy)`. Если лог шлёт ошибки сборки — показать хвост лога пользователю.

## Проверка после деплоя

- Контейнеры: `docker ps --filter name=scada-mobile --format '{{.Names}} {{.Status}}'` — 3 шт., все `(healthy)`.
- Бэкенд стартовал: в логе `backend/logs/scada.mobile.backend.json` (JSON-строки) строка `"message":"Started Application` — grep: `grep -a "Started Application" ~/scada-mobile/backend/logs/scada.mobile.backend.json | tail -1`. **Важно:** `docker logs scada-mobile-backend-1` почти пуст — консольный appender отключён, реальные логи только в этом файле, grep обязателен с `-a` (бинарные NUL).
- Миграции применились: `docker exec scada-mobile-postgres psql -U scada_user -d scada_mobile -tAc "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3"` (creds: user `scada_user`, db `scada_mobile`).

## Отчёт пользователю

Коротко: новый HEAD (`git log --oneline -1`), статус 3 контейнеров, применённые миграции (номера V*). Любая остановка на шаге — отчитаться, где и почему, ничего не доделывать обходными путями.

## Common Mistakes

| Ошибка | Правильно |
|--------|-----------|
| Прямой вызов `make docker-prod-up` в MCP | Упрётся в 60-сек таймаут и оборвётся на полпути → nohup + поллинг |
| Использовать `docker-prod-recreate` при обычном обновлении | Только `docker-prod-down` (stop). Recreate — полное удаление контейнеров, только «на всякий случай» |
| Читать логи через `docker logs backend` | Пусто; логи в `backend/logs/*.json` |
| Трогать контейнеры erp/kafka/redis «заодно» | Запрещено. Только scada-mobile-* |
| `git pull` без `--ff-only` | Может создать merge-коммит; только `--ff-only` |
| `cat .env.prod.local` «для проверки» | Секреты. Только `test -f` |
