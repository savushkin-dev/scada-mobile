# SCADA Mobile: пошаговый запуск в PROD

> **Требование:** установлены Docker и Docker Compose.

## Содержание
- [SCADA Mobile: пошаговый запуск в PROD](#scada-mobile-пошаговый-запуск-в-prod)
  - [Содержание](#содержание)
  - [Шаг 1: Клонировать репозиторий](#шаг-1-клонировать-репозиторий)
  - [Шаг 2: Создать файл окружения](#шаг-2-создать-файл-окружения)
  - [Шаг 3: Сгенерировать секреты](#шаг-3-сгенерировать-секреты)
    - [3.1 JWT-секреты (обязательно)](#31-jwt-секреты-обязательно)
    - [3.2 Пароль PostgreSQL (обязательно)](#32-пароль-postgresql-обязательно)
    - [3.3 Пароль администратора (обязательно)](#33-пароль-администратора-обязательно)
  - [Шаг 4: Заполнить обязательные переменные](#шаг-4-заполнить-обязательные-переменные)
  - [Шаг 5: Запустить стек](#шаг-5-запустить-стек)
  - [Шаг 6: Получить учётные данные администратора](#шаг-6-получить-учётные-данные-администратора)
  - [Диагностика](#диагностика)
    - [Логи](#логи)
    - [Проверка health backend](#проверка-health-backend)
    - [Проверка PostgreSQL](#проверка-postgresql)
    - [Проверка WebSocket](#проверка-websocket)
  - [Остановка](#остановка)
  - [Если что-то не стартовало](#если-что-то-не-стартовало)

---

## Шаг 1: Клонировать репозиторий

~~~bash
git clone https://github.com/savushkin-dev/scada-mobile.git
cd scada-mobile
~~~

---

## Шаг 2: Создать файл окружения

**Windows PowerShell:**
~~~powershell
Copy-Item .env.prod.example .env.prod.local
~~~

**Linux / macOS:**
~~~bash
cp .env.prod.example .env.prod.local
~~~

---

## Шаг 3: Сгенерировать секреты

Все переменные имеют префикс `SCADA_MOBILE_`. Выполните команды и скопируйте вывод в `.env.prod.local`.

### 3.1 JWT-секреты (обязательно)

**bash / Linux / macOS:**
~~~bash
export SCADA_MOBILE_JWT_ACCESS_SECRET=$(openssl rand -base64 32)
export SCADA_MOBILE_JWT_REFRESH_SECRET=$(openssl rand -base64 32)
echo "ACCESS:  $SCADA_MOBILE_JWT_ACCESS_SECRET"
echo "REFRESH: $SCADA_MOBILE_JWT_REFRESH_SECRET"
~~~

**PowerShell:**
~~~powershell
$env:SCADA_MOBILE_JWT_ACCESS_SECRET = openssl rand -base64 32
$env:SCADA_MOBILE_JWT_REFRESH_SECRET = openssl rand -base64 32
Write-Host "ACCESS:  $env:SCADA_MOBILE_JWT_ACCESS_SECRET"
Write-Host "REFRESH: $env:SCADA_MOBILE_JWT_REFRESH_SECRET"
~~~

### 3.2 Пароль PostgreSQL (обязательно)

**bash / Linux / macOS:**
~~~bash
export SCADA_MOBILE_DATABASE_PASSWORD=$(openssl rand -base64 16)
echo "DB_PASS: $SCADA_MOBILE_DATABASE_PASSWORD"
~~~

**PowerShell:**
~~~powershell
$env:SCADA_MOBILE_DATABASE_PASSWORD = openssl rand -base64 16
Write-Host "DB_PASS: $env:SCADA_MOBILE_DATABASE_PASSWORD"
~~~

### 3.3 Пароль администратора (обязательно)

Сгенерируйте и запишите в `.env.prod.local`:

**bash / Linux / macOS:**
~~~bash
export SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE=$(openssl rand -base64 6 | tr -dc 'A-Z0-9' | cut -c1-8)
export SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD=$(openssl rand -base64 7 | cut -c1-10)
echo "ADMIN_CODE: $SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE"
echo "ADMIN_PASS: $SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD"
~~~

**PowerShell:**
~~~powershell
$passChars = (48..57) + (65..90) + (97..122) + (33,35,36,37,38,42,64,94)
$codeChars = (48..57) + (65..90)
$env:SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD = -join ($passChars | Get-Random -Count 10 | ForEach-Object { [char]$_ })
$env:SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE = -join ($codeChars | Get-Random -Count 8 | ForEach-Object { [char]$_ })
Write-Host "ADMIN_PASS: $env:SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD"
Write-Host "ADMIN_CODE: $env:SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE"
~~~

> **Требования к паролю администратора:** ровно 10 символов, заглавные и строчные буквы, цифры, спецсимволы.  
> **Требования к коду администратора:** рекомндуется написать просто "admin"

---

## Шаг 4: Заполнить обязательные переменные

Откройте `.env.prod.local` и убедитесь, что заполнены:

| Переменная | Описание | Пример |
|-----------|----------|--------|
| `SCADA_MOBILE_BACKEND_PORT` | Порт backend | `9999` |
| `SCADA_MOBILE_FRONTEND_PORT` | Порт frontend | `9998` |
| `SCADA_MOBILE_POSTGRES_PORT` | Порт PostgreSQL | `5432` |
| `SCADA_MOBILE_DATABASE_PASSWORD` | Пароль БД | из шага 3.2 |
| `SCADA_MOBILE_JWT_ACCESS_SECRET` | JWT access secret | из шага 3.1 |
| `SCADA_MOBILE_JWT_REFRESH_SECRET` | JWT refresh secret | из шага 3.1 |
| `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS` | Origin фронтенда | `http://localhost:9998` |

**Критично:** `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS` должен совпадать с точным origin в адресной строке браузера. Для нескольких origin — через запятую без пробелов: `http://localhost:9998,http://192.168.1.10:9998`

**Минимальный `.env.prod.local`:**

~~~env
SCADA_MOBILE_BACKEND_PORT=9999
SCADA_MOBILE_FRONTEND_PORT=9998
SCADA_MOBILE_POSTGRES_PORT=5432
SCADA_MOBILE_DATABASE_PASSWORD=YOUR_GENERATED_DB_PASSWORD
SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS=http://999.9.9.9:9998
SCADA_MOBILE_JWT_ACCESS_SECRET=YOUR_GENERATED_ACCESS_SECRET
SCADA_MOBILE_JWT_REFRESH_SECRET=YOUR_GENERATED_REFRESH_SECRET

SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD=YOUR_GENERATED_ADMIN_PASS
SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE=YOUR_GENERATED_ADMIN_CODE

# Один автомат для проверки (остальные можно не указывать)
SCADA_MOBILE_PRINTSRV_HASSIA4_HOST=999.9.9.9
SCADA_MOBILE_PRINTSRV_HASSIA4_PORT=9999
~~~

> Не подключенные автоматы отобразятся как «Нет данных» — это нормально.

---

## Шаг 5: Запустить стек

~~~bash
make docker-prod-up
~~~

Подождите, пока все контейнеры станут `healthy`:

~~~bash
make docker-ps
~~~

Ожидаемый результат:
~~~
NAME                      STATUS                    PORTS
scada-mobile-postgres     healthy                   0.0.0.0:5432->5432/tcp
scada-mobile-backend-1    healthy                   0.0.0.0:9999->8080/tcp
scada-mobile-frontend-1   healthy                   0.0.0.0:9998->8080/tcp
~~~

Фронтенд доступен по адресу: `http://<IP>:<FRONTEND_PORT>`

---

## Шаг 6: Получить учётные данные администратора

При первом запуске backend автоматически создаёт администратора и дописывает учётные данные в `.env.prod.local`:

~~~bash
grep SCADA_MOBILE_ADMIN .env.prod.local
~~~

Ожидаемый вывод:
~~~
SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE=XXXXXXXX
SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD=XXXXXXXXXX
~~~

> **Важно:** эти переменные создаются только при первом старте, когда в БД нет пользователя с ролью ADMIN. Сохраните их надёжно.

---

## Диагностика

### Логи

~~~bash
# Все сервисы
make docker-logs

# Только backend
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs -f backend

# Только PostgreSQL
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs -f postgres
~~~

### Проверка health backend

~~~bash
curl -s http://localhost:<BACKEND_PORT>/actuator/health
~~~

### Проверка PostgreSQL

~~~bash
docker exec scada-mobile-postgres pg_isready -U scada_user -d scada_mobile
~~~

### Проверка WebSocket

~~~bash
curl -i \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Origin: http://<YOUR_ORIGIN>" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  http://<YOUR_ORIGIN>/ws/live
~~~

- `101 Switching Protocols` — origin разрешён
- `403` — origin не входит в `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS`

---

## Остановка

~~~bash
make docker-prod-down
~~~

Данные PostgreSQL сохраняются в Docker-томе `scada-mobile-postgres-data`.

---

## Если что-то не стартовало

1. **Порт занят:** проверьте `SCADA_MOBILE_BACKEND_PORT`, `SCADA_MOBILE_FRONTEND_PORT`, `SCADA_MOBILE_POSTGRES_PORT`
2. **Пустые секреты:** убедитесь, что `SCADA_MOBILE_JWT_ACCESS_SECRET`, `SCADA_MOBILE_JWT_REFRESH_SECRET`, `SCADA_MOBILE_DATABASE_PASSWORD` не пустые
3. **CORS ошибки:** проверьте `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS`
4. **Пересобрать без кеша:**

~~~bash
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml build --no-cache
docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml up -d
~~~

5. Очистить всё:

~~~bash
docker compose -f docker-compose.prod.yml --env-file .env.prod.local down -v --rmi all
~~~
