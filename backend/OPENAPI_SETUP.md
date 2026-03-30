# OpenAPI и профили Spring Boot (Backend)

Актуальность: 30.03.2026.

## Что уже настроено

## 1. Профили окружения

В backend используются три YAML-файла:

- `src/main/resources/application.yaml` — общие настройки;
- `src/main/resources/application-dev.yaml` — dev-профиль;
- `src/main/resources/application-prod.yaml` — prod-профиль.

## 2. OpenAPI / Swagger

В `build.gradle.kts` подключен springdoc для ветки Spring Boot 4:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
```

## 3. Базовый API путь

В `application.yaml` задано:

```yaml
scada:
  api:
    version: v1.0.0
    base-path: /api/${scada.api.version}
```

Итоговый префикс REST API: `/api/v1.0.0`.

## Как это работает по профилям

## Dev

- OpenAPI и Swagger включены;
- `springdoc.api-docs.path = /v3/api-docs`;
- `springdoc.swagger-ui.path = /swagger-ui.html`;
- можно использовать для разработки и ручной проверки контракта.

## Prod

- OpenAPI и Swagger отключены;
- публичная поверхность API минимальна;
- документация не должна быть доступна извне.

## Быстрый запуск

Из корня репозитория:

```bash
make back-run
```

Это запускает backend в `dev` профиле на порту `8080`.

Запуск в `prod` профиле:

```bash
make back-run-prod BACKEND_PORT=8080
```

## Где проверять

После старта в `dev`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health liveness: `http://localhost:8080/api/v1.0.0/health/live`
- Health readiness: `http://localhost:8080/api/v1.0.0/health/ready`

## Важные замечания

- В `prod` отсутствие Swagger — ожидаемое и правильное поведение.
- Если URL API меняется, корректировки делаются через `scada.api.version` и `scada.api.base-path` в `application.yaml`.
- Для frontend-контракта источником истины служат [../FRONTEND_API.md](../FRONTEND_API.md) и [../api_mapping.md](../api_mapping.md).

## Troubleshooting

## Swagger не открывается

Проверьте:

1. backend запущен именно в `dev` профиле;
2. приложение стартовало без ошибок;
3. используется URL `http://localhost:8080/swagger-ui.html`.

## Не открывается `/v3/api-docs`

Проверьте:

1. не запущен ли `prod` профиль;
2. нет ли конфликтов порта;
3. что сборка прошла с актуальными зависимостями Gradle.
