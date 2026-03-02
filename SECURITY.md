# Политика безопасности

## Секреты и ключи

- **Не коммитьте** в репозиторий токены, пароли, приватные ключи, файлы keystore (`*.jks`, `*.keystore`) и любые credentials.
- В Android-части ключи подписи хранятся **вне репозитория** — локально у разработчика или в защищённом хранилище CI (GitHub Secrets).
- Все production-значения (URL, SHA-256 отпечатки сертификатов, ключи API) настраиваются через переменные окружения или CI-секреты — никаких хардкодов.
- Файлы с секретами (`.env`, `local.properties`, keystore) должны быть добавлены в `.gitignore`.

## Android Keystore

- Keystore создаётся один раз и хранится в безопасном месте (не в Git).
- SHA-256 отпечаток публичного ключа размещается в `frontend/well-known/assetlinks.json` — это не секрет, это публичная информация.
- При утере keystore пересборка и подписание новым ключом потребуют обновления `assetlinks.json` и перевыпуска APK.

> Инструкции по сборке и подписанию APK — в [`android/README.md`](android/README.md).

## Backend

- В dev-профиле Swagger UI (`/swagger-ui.html`) и `/v3/api-docs` **включены** — только для разработки.
- В prod-профиле Swagger UI и OpenAPI-документация **отключены**.
- Spring Boot Actuator endpoints в prod должны быть закрыты от внешней сети или защищены (отдельный порт, файрволл).

> Конфигурация профилей — в [`backend/OPENAPI_SETUP.md`](backend/OPENAPI_SETUP.md). Настройка Actuator — в [`backend/LOGGING.md`](backend/LOGGING.md).

## CI/CD (GitHub Actions)

Инструменты безопасности, встроенные в пайплайны:

| Инструмент | Назначение |
|---|---|
| **TruffleHog** | Сканирование коммитов на наличие секретов |
| **Dependabot** | Автообновление зависимостей с известными уязвимостями |
| **CodeQL** | Статический анализ кода на уязвимости |
| **OWASP Dependency Check** | Проверка зависимостей по базе CVE |

> Описание CI/CD пайплайнов — в [`STRUCTURE.md`](STRUCTURE.md).
