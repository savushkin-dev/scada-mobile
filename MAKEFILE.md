# Makefile (SCADA Mobile)

## Purpose
Документ описывает доступные цели Makefile и их назначение. Источник правды — файл [Makefile](Makefile).

## Table of contents
- [Purpose](#purpose)
- [Defaults](#defaults)
- [Help target](#help-target)
- [Backend targets](#backend-targets)
- [Frontend targets](#frontend-targets)
- [Docker targets](#docker-targets)
- [Android targets](#android-targets)

## Defaults
Переменные по умолчанию определены в [Makefile](Makefile#L3-L8).

| Переменная | Значение | Описание |
| --- | --- | --- |
| `DEV_BACKEND_PORT` | 8080 | Порт backend в dev |
| `DEV_FRONTEND_PORT` | 5500 | Порт frontend в dev |
| `SEED_DB_CONTAINER` | postgres | Имя контейнера Postgres для `db-seed` |
| `SEED_DB_NAME` | scada_mobile | Имя БД для `db-seed` |
| `SEED_DB_USER` | scada_user | Пользователь БД для `db-seed` |
| `SEED_DB_PASSWORD` |  | Пароль БД для `db-seed` (обязателен) |
| `SEED_SQL` | scripts/seed_notifications.sql | Путь к seed-скрипту |

Файл окружения prod задается через `PROD_ENV_FILE` ([Makefile](Makefile#L22-L24)).

## Help target
`make help` выводит краткий список целей для Windows и Unix, см. [Makefile](Makefile#L26-L72).

## Backend targets

| Цель | Назначение | Реализация |
| --- | --- | --- |
| `back-run` | Запуск backend в dev-профиле | [Makefile](Makefile#L74-L83) |
| `back-run-prod` | Запуск backend в prod-профиле с `BACKEND_PORT` | [Makefile](Makefile#L78-L92) |

## Frontend targets

| Цель | Назначение | Реализация |
| --- | --- | --- |
| `front-install` | Установка зависимостей frontend | [Makefile](Makefile#L95-L113) |
| `front-dev` | Запуск Vite dev-сервера | [Makefile](Makefile#L99-L116) |
| `front-build` | Production сборка frontend | [Makefile](Makefile#L102-L118) |

## Docker targets

| Цель | Назначение | Реализация |
| --- | --- | --- |
| `docker-prod-up` | Старт prod-стека | [Makefile](Makefile#L125-L132) |
| `docker-prod-down` | Остановка prod-стека | [Makefile](Makefile#L134-L135) |
| `docker-ps` | Статус контейнеров | [Makefile](Makefile#L137-L141) |

## Database targets

| Цель | Назначение | Реализация |
| --- | --- | --- |
| `db-seed` | Заполнение БД seed-данными через docker exec | [Makefile](Makefile#L95-L114) |

## Android targets

| Цель | Назначение | Реализация |
| --- | --- | --- |
| `bwa-init` | Инициализация TWA проекта | [Makefile](Makefile#L105-L121) |
| `bwa-build-apk` | Сборка APK через Bubblewrap | [Makefile](Makefile#L108-L124) |
