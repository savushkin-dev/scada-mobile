# Логирование backend (SCADA Mobile)

## Purpose
Фактические правила логирования на основе текущей конфигурации logback и профилей backend.

## Table of contents
- [Purpose](#purpose)
- [Source of truth](#source-of-truth)
- [Log destinations](#log-destinations)
- [Dev vs prod](#dev-vs-prod)
- [Rotation policy](#rotation-policy)
- [Request context (MDC)](#request-context-mdc)
- [Runtime level changes](#runtime-level-changes)
- [Do not log](#do-not-log)

## Source of truth
- Конфигурация logback: [backend/src/main/resources/logback-spring.xml](backend/src/main/resources/logback-spring.xml).
- Профили dev/prod: [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml), [backend/src/main/resources/application-prod.yaml](backend/src/main/resources/application-prod.yaml).
- Имя приложения (используется в имени файлов): [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml).

## Log destinations
- Базовый путь задается переменной `LOG_PATH` (дефолт `./logs`) в [backend/src/main/resources/logback-spring.xml](backend/src/main/resources/logback-spring.xml).
- Имена файлов основаны на `spring.application.name` из [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml).
- Архивы ротации пишутся в подкаталог `archived` (см. [backend/src/main/resources/logback-spring.xml](backend/src/main/resources/logback-spring.xml)).

## Dev vs prod
- Dev: консоль + plain text файл, повышенные уровни для кода проекта (см. [backend/src/main/resources/logback-spring.xml](backend/src/main/resources/logback-spring.xml)).
- Prod: JSON-логи через AsyncAppender, root=WARN и backend=INFO (см. [backend/src/main/resources/logback-spring.xml](backend/src/main/resources/logback-spring.xml)).

## Rotation policy
Политика ротации (size+time, 50MB, 30 дней, 500MB total) задана в [backend/src/main/resources/logback-spring.xml](backend/src/main/resources/logback-spring.xml).

## Request context (MDC)
MDC-контекст добавляется фильтром `MdcFilter`, который проставляет `requestId`, `method`, `uri` и регистрируется с наивысшим приоритетом (см. [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcFilter.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcFilter.java), [backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcConfig.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/config/MdcConfig.java)).

## Runtime level changes
Уровни логов можно менять через Actuator endpoint `POST /actuator/loggers/{loggerName}`. Доступность loggers включена в prod-профиле (см. [backend/src/main/resources/application-prod.yaml](backend/src/main/resources/application-prod.yaml)).

## Do not log
- Пароли, токены, ключи и любые секреты.
- Большие payload-объекты на `INFO`.
- Однотипные сообщения в горячих циклах на `INFO`.