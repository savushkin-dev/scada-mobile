# OpenAPI и профили Spring Boot (Backend)

## Purpose
Описание текущей конфигурации профилей и OpenAPI/Swagger на основе фактических файлов backend.

## Table of contents
- [Purpose](#purpose)
- [Profiles and config files](#profiles-and-config-files)
- [OpenAPI and Swagger](#openapi-and-swagger)
- [API base path](#api-base-path)
- [Run and verify](#run-and-verify)
- [Notes](#notes)
- [Troubleshooting](#troubleshooting)

## Profiles and config files
- Общие настройки: [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml).
- Dev-профиль: [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml).
- Prod-профиль: [backend/src/main/resources/application-prod.yaml](backend/src/main/resources/application-prod.yaml).

## OpenAPI and Swagger
- Springdoc подключен в Gradle: [backend/build.gradle.kts](backend/build.gradle.kts).
- В dev-профиле OpenAPI и Swagger UI включены и имеют фиксированные пути (см. [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml)).
- В prod-профиле OpenAPI и Swagger UI отключены (см. [backend/src/main/resources/application-prod.yaml](backend/src/main/resources/application-prod.yaml)).

## API base path
Префикс REST API определяется через `scada.api.version` и `scada.api.base-path` в [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml).

## Run and verify
- Запуск dev-профиля: `make back-run` (см. [MAKEFILE.md](../MAKEFILE.md)).
- Запуск prod-профиля: `make back-run-prod BACKEND_PORT=<port>` (см. [MAKEFILE.md](../MAKEFILE.md)).
- Swagger UI и OpenAPI JSON доступны только в dev-профиле и используют пути из [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml).
- Health endpoints описаны в [API_REFERENCE.md](../API_REFERENCE.md).

## Notes
- Изменение версии API производится в [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml).
- Источник истины для фронтенд-контракта: [API_REFERENCE.md](../API_REFERENCE.md), [FRONTEND_API.md](../FRONTEND_API.md), [api_mapping.md](../api_mapping.md).

## Troubleshooting
### Swagger не открывается
- Проверьте, что backend запущен в dev-профиле и применен [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml).
- Убедитесь, что приложение стартовало без ошибок и порт доступен.

### Не открывается `/v3/api-docs`
- Проверьте, что не запущен prod-профиль.
- Убедитесь, что конфигурация springdoc не отключена в [backend/src/main/resources/application-dev.yaml](backend/src/main/resources/application-dev.yaml).