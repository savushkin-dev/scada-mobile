# Политика безопасности

## Purpose
Краткие правила хранения секретов и доступа к backend-эндпоинтам на основе текущей структуры репозитория.

## Table of contents
- [Purpose](#purpose)
- [Secrets and keys](#secrets-and-keys)
- [Android keystore](#android-keystore)
- [Backend exposure](#backend-exposure)
- [CI/CD status](#cicd-status)

## Secrets and keys
- Не коммитьте токены, пароли, приватные ключи, keystore (`*.jks`, `*.keystore`) и любые credentials.
- Production значения (URL, отпечатки сертификатов, API-ключи) должны задаваться через env/CI-секреты, без хардкода.
- Файлы с секретами (`.env`, `local.properties`, keystore) должны быть исключены в [/.gitignore](.gitignore).

## Android keystore
- Keystore создается один раз и хранится вне репозитория.
- SHA-256 отпечаток публичного ключа публикуется в [frontend/public/well-known/assetlinks.json](frontend/public/well-known/assetlinks.json).
- При смене ключа требуется обновить `assetlinks.json` и перевыпустить APK (см. [android/README.md](android/README.md)).

## Backend exposure
- В dev-профиле Swagger UI и `/v3/api-docs` включены только для разработки (см. [backend/OPENAPI_SETUP.md](backend/OPENAPI_SETUP.md)).
- В prod-профиле OpenAPI и Swagger UI отключены (см. [backend/OPENAPI_SETUP.md](backend/OPENAPI_SETUP.md)).
- Actuator endpoints в prod должны быть закрыты от внешней сети (подробнее в [backend/LOGGING.md](backend/LOGGING.md)).

## CI/CD status
В репозитории нет настроенных CI/CD пайплайнов или GitHub Actions. Если пайплайны будут добавляться, обеспечить секрет-сканирование и контроль уязвимостей на уровне CI.