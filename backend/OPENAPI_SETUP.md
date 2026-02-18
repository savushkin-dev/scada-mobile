# OpenAPI и профили Spring Boot - Инструкция по завершению настройки

## Что было сделано

### 1. Созданы профили dev и prod

- `src/main/resources/application-dev.yaml` - профиль для разработки
- `src/main/resources/application-prod.yaml` - профиль для продакшена
- `src/main/resources/application.yaml` - базовая конфигурация (общие параметры)

### 2. Добавлена зависимость springdoc-openapi

В `build.gradle.kts` добавлено:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
```

### 3. Добавлены OpenAPI аннотации

- **Контроллер**: `CommandsController.java` - @Operation, @ApiResponses, @Parameter
- **DTO**: QueryStateResponseDTO, ChangeCommandResponseDTO, UnitStateDTO, UnitPropertiesDTO - @Schema
- **Конфиг**: `OpenApiConfig.java` - метаданные API

### 4. Настройки профилей

#### Dev (разработка)

- Логирование: DEBUG/TRACE для пакета backend
- Polling: 5000 ms (5 секунд) - медленный цикл
- OpenAPI: **включено**
    - `/v3/api-docs` - JSON спецификация
    - `/swagger-ui.html` - интерактивный UI

#### Prod (продакшен)

- Логирование: WARN/INFO - минимальное
- Polling: 500 ms (0.5 секунды) - быстрый цикл
- Retry: больше попыток (10 вместо 5)
- OpenAPI: **отключено** (безопасность)

## Как запустить

### 1. Пересоберите проект

```powershell
.\gradlew clean build -x test
```

Если возникнут ошибки компиляции из-за springdoc:

1. Закройте IntelliJ IDEA
2. Удалите `.gradle` папку и папку `build`
3. Откройте проект заново - IntelliJ автоматически скачает зависимости

### 2. Запуск с профилем dev (по умолчанию)

```powershell
# Через Gradle
.\gradlew bootRun

# Или через jar
java -jar build/libs/scada.mobile.backend-0.0.1-SNAPSHOT.jar
```

### 3. Запуск с профилем prod

```powershell
# Через переменную окружения
$env:SPRING_PROFILES_ACTIVE="prod"
.\gradlew bootRun

# Или через аргумент
.\gradlew bootRun --args="--spring.profiles.active=prod"

# Или через jar
java -jar build/libs/scada.mobile.backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 4. Проверка OpenAPI (в dev)

После запуска с профилем dev:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## Архитектурные гарантии

Все изменения соблюдают инварианты:
✅ GET читает только snapshot (не ходит в PrintSrv)
✅ POST быстрый (< 50ms), команды в буфер
✅ Scan cycle остался последовательным (READ → LOGIC → WRITE → UPDATE)
✅ Буфер thread-safe с Last-Write-Wins
✅ OpenAPI документация только на HTTP слое (не влияет на domain/application)

## Что документировано

### GET /api/v1/commands/queryAll

- Возвращает snapshot состояния (все units)
- Может быть устаревшим при проблемах с PrintSrv
- 200 OK - snapshot получен
- 503 Service Unavailable - snapshot не готов

### POST /api/v1/commands/setUnitVars

- Принимает команду в буфер
- Query params: `unit` (1-based), `value` (integer >= 1)
- Eventual consistency (изменения видны после scan cycle)
- Last-Write-Wins для одного unit
- 200 OK - команда принята
- 400 Bad Request - неверные параметры
- 503 Service Unavailable - буфер переполнен

### GET /api/v1/commands/health/live

- Liveness probe (приложение работает)
- 200 OK - alive

### GET /api/v1/commands/health/ready

- Readiness probe (snapshot загружен)
- 200 OK - готов
- 503 - не готов

## Безопасность в prod

В продакшене:

- Swagger UI **отключен**
- `/v3/api-docs` **отключен**
- Логирование минимальное (не спамит файлы)
- Polling быстрый (0.5 сек)

## Troubleshooting

### Ошибка "Cannot resolve symbol 'swagger'"

**Решение**: Обновите зависимости Gradle

```powershell
.\gradlew --refresh-dependencies clean build
```

### IntelliJ не видит классы springdoc

**Решение**: Reimport Gradle project

- File → Invalidate Caches and Restart
- Или: View → Tool Windows → Gradle → Reload All Gradle Projects

### Swagger UI не открывается

**Проверьте**:

1. Запущен профиль `dev` (не `prod`)
2. Приложение стартовало без ошибок
3. URL правильный: http://localhost:8080/swagger-ui.html (не swagger-ui/)

### Polling слишком быстрый/медленный

**Изменить**: В application-dev.yaml или application-prod.yaml

```yaml
printsrv:
  polling:
    fixed-delay-ms: 5000  # миллисекунды
```

---
**Автор**: GitHub Copilot  
**Дата**: 2026-02-18

