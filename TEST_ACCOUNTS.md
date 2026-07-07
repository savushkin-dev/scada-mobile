# TEST_ACCOUNTS.md — Тестовые данные и инструкции для Playwright-тестирования

> Файл для нейроагентов (Kimi, Kimi K2.7 и др.).
> Содержит: тестовые аккаунты, данные из БД, способы запуска сервисов, проверки состояния.
> Расположение: `C:\Users\kseni\Documents\University\4-course\Diploma\Source\scada-mobile\TEST_ACCOUNTS.md`

---

## 1. Тестовые аккаунты (логин / пароль)

Пароль у всех тестовых пользователей одинаковый: **password**

| Роль | Код (логин) | Полное имя | user_id | role_id |
|------|-------------|------------|---------|---------|
| Master (мастер цеха) | `USR-000001` | Test User | 1 | 1 |
| Master (мастер цеха) | `USR-000002` | Second User | 2 | 1 |
| ADMIN | `ADM-000001` | Admin User | 3 | 2 |

### Пароли для всех пользователей одинаковые
```
password
```

### Назначения пользователей
- **Test User (USR-000001)** — привязан к аппаратам: Trepko №1 (unit_id=1), Hassia №1 (unit_id=3)
- **Second User (USR-000002)** — привязан к аппаратам: Trepko №2 (unit_id=2), Hassia №2 (unit_id=4)
- **Admin User (ADM-000001)** — привязан к аппарату: Trepko №1 (unit_id=1)

Все пользователи имеют включённые уведомления (`incident_notifications_enabled=true`, `android_call_notifications_enabled=true`) на всех аппаратах.

---

## 2. Тестовые данные: цеха и аппараты

### Цеха (workshops)
| workshop_id | Название |
|-------------|----------|
| 1 | Цех десертов |
| 2 | Цех розлива |

### Аппараты (units)
| unit_id | Цех | Название | PrintSrv instance | Хост | Порт |
|---------|-----|----------|-------------------|------|------|
| 1 | Цех десертов | Trepko №1 | `trepko1` | 192.168.1.10 | 9100 |
| 2 | Цех десертов | Trepko №2 | `trepko2` | 192.168.1.11 | 9100 |
| 3 | Цех десертов | Hassia №1 | `hassia1` | 192.168.1.12 | 9100 |
| 4 | Цех десертов | Hassia №2 | `hassia2` | 192.168.1.13 | 9100 |
| 5 | Цех десертов | Hassia №4 | `hassia4` | 192.168.1.14 | 9100 |
| 6 | Цех десертов | Hassia №5 | `hassia5` | 192.168.1.15 | 9100 |
| 7 | Цех десертов | Hassia №6 | `hassia6` | 192.168.1.16 | 9100 |
| 8 | Цех десертов | Hassia №3 | `hassia3` | 192.168.1.17 | 9100 |
| 9 | Цех десертов | Bosch | `bosch` | 192.168.1.18 | 9100 |
| 10 | Цех десертов | Grunwald №5 | `grunwald5` | 192.168.1.19 | 9100 |
| 11 | Цех десертов | Grunwald №8 | `grunwald8` | 192.168.1.20 | 9100 |
| 12 | Цех розлива | Grunwald №1 | `grunwald1` | 192.168.1.21 | 9100 |
| 13 | Цех розлива | Grunwald №2 | `grunwald2` | 192.168.1.22 | 9100 |
| 14 | Цех розлива | Grunwald №11 | `grunwald11` | 192.168.1.23 | 9100 |

### Типы устройств (device_types)
| code | Название |
|------|----------|
| `printer` | Принтер |
| `aggregation_cam` | Камера агрегации |
| `aggregation_box_cam` | Камера агрегации на коробе |
| `checker_cam` | Камера проверки |

### Устройства на аппаратах (unit_devices) — ключевые примеры
- **Trepko №1/№2**: CamBatch, CamPacker, CamPackerBox, CamChecker
- **Hassia №4/№5/№6/№3**: Printer11, Printer12
- **Bosch**: Printer11, Printer12
- **Grunwald №5/№8**: Printer11, Printer12
- **Grunwald №1/№2**: Printer11, Printer12, CamChecker, CamEanChecker1–4
- **Grunwald №11**: Printer11–14, CamAgregation1–2, CamAgregationBox1–2, CamChecker1–2, CamEanChecker1–4

---

## 3. Предпочтительный способ запуска сервисов (Windows)

> ⚠️ **Важно**: Команды `make` из Makefile на Windows НЕ работают корректно в bash-окружении нейроагента (Git Bash). Используйте прямые команды ниже.

### 3.1. Проверка состояния PostgreSQL (БД)

```bash
# Проверить, запущен ли контейнер PostgreSQL (dev-режим)
docker ps --filter "name=postgres" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Если контейнер не запущен — запустить (типичная команда для dev):
docker run -d --name postgres -p 5432:5432 -e POSTGRES_DB=scada_mobile -e POSTGRES_USER=scada_user -e POSTGRES_PASSWORD=scada_password postgres:15

# Или если используется docker-compose (dev-стек):
# docker-compose -f docker-compose.yml up -d postgres
```

**Если БД не запущена** — backend не стартанет. Проверяйте БД ПЕРВЫМ делом.

### 3.2. Запуск Backend (Spring Boot, dev-профиль)

```bash
# Перейти в папку backend
cd C:/Users/kseni/Documents/University/4-course/Diploma/Source/scada-mobile/backend

# Установить переменные окружения и запустить через gradlew.bat
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
export SPRING_PROFILES_ACTIVE=dev
export SERVER_PORT=8080
export SCADA_MOBILE_DATABASE_URL="jdbc:postgresql://localhost:5432/scada_mobile"
export SCADA_MOBILE_DATABASE_USERNAME="scada_user"
export SCADA_MOBILE_DATABASE_PASSWORD="scada_password"
# JWT-секреты (можно любые 32+ символа base64, для dev подойдут фиксированные)
export SCADA_MOBILE_JWT_ACCESS_SECRET="43567c4y59827cvn89cn7482y1837q23n8x4234r8974238c4857n49"
export SCADA_MOBILE_JWT_REFRESH_SECRET="4378v568590yftn7834vqcr78cyxr89c4xctg/54tc3uv98qc3n78="

# Запуск в фоне (через nohup или просто в отдельном терминале)
./gradlew.bat bootRun > backend.log 2>&1 &
echo $! > .backend.pid
```

**Проверка что backend запущен:**
```bash
# Проверить процесс
curl -s http://localhost:8080/api/v1/commands/health/live || echo "Backend НЕ отвечает"

# Или проверить PID-файл
cat C:/Users/kseni/Documents/University/4-course/Diploma/Source/scada-mobile/backend/.backend.pid 2>/dev/null && echo "PID-файл есть" || echo "Backend не запущен (нет PID-файла)"
```

**Остановка backend:**
```bash
cd C:/Users/kseni/Documents/University/4-course/Diploma/Source/scada-mobile/backend
if [ -f .backend.pid ]; then
    kill $(cat .backend.pid) && rm .backend.pid
    echo "Backend остановлен"
else
    echo "Нет PID-файла — backend возможно не запущен"
fi
```

### 3.3. Запуск Frontend (Vite dev server)

```bash
# Перейти в папку frontend
cd C:/Users/kseni/Documents/University/4-course/Diploma/Source/scada-mobile/frontend

# Если node_modules отсутствуют — установить зависимости
npm install

# Запуск dev-сервера на порту 5500
npm run dev -- --port 5500 --strictPort
```

**Проверка что frontend запущен:**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:5500
# Должно вернуть 200
```

**Остановка frontend:** Ctrl+C в терминале где запущен `npm run dev`.

### 3.4. Быстрая проверка всех сервисов перед тестом

```bash
# 1. PostgreSQL
docker ps --filter "name=postgres" --format "{{.Names}}: {{.Status}}"

# 2. Backend
curl -s http://localhost:8080/api/v1/commands/health/live && echo " ✓ Backend OK" || echo " ✗ Backend НЕ отвечает"

# 3. Frontend
curl -s -o /dev/null -w "%{http_code}" http://localhost:5500 | grep -q "200" && echo " ✓ Frontend OK" || echo " ✗ Frontend НЕ отвечает"
```

---

## 4. Seed-данные (SQL)

Файл с полным набором тестовых данных: `scripts/seed_notifications.sql`

### Применение seed-данных (dev-БД)

```bash
cd C:/Users/kseni/Documents/University/4-course/Diploma/Source/scada-mobile

# Если PostgreSQL в Docker:
docker exec -i -e PGPASSWORD=scada_password postgres psql -U scada_user -d scada_mobile -v ON_ERROR_STOP=1 -f scripts/seed_notifications.sql

# Если PostgreSQL локально:
# PGPASSWORD=scada_password psql -U scada_user -d scada_mobile -h localhost -p 5432 -v ON_ERROR_STOP=1 -f scripts/seed_notifications.sql
```

### Prod seed (только цеха, аппараты, устройства — без пользователей)

```bash
docker exec -i -e PGPASSWORD=db_password scada-mobile-postgres psql -U scada_user -d scada_mobile -v ON_ERROR_STOP=1 -f scripts/seed_prod_data.sql
```

---

## 5. Конфигурация окружения (dev)

### Портовая карта
| Сервис | Порт | URL |
|--------|------|-----|
| PostgreSQL | 5432 | localhost:5432 |
| Backend (dev) | 8080 | http://localhost:8080 |
| Frontend (dev) | 5500 | http://localhost:5500 |
| Swagger UI | — | http://localhost:8080/swagger-ui.html |

### CORS (dev-профиль)
Backend разрешает запросы с:
- `http://localhost:*`
- `http://127.0.0.1:*`
- `http://192.168.*:*`
- `http://10.*:*`
- `https://*.ngrok-free.dev`
- `https://*.ngrok-free.app`
- `https://*.lhr.life`
- `http://*.lhr.life`

### Базовые env-переменные для dev
```bash
export SCADA_MOBILE_DATABASE_URL="jdbc:postgresql://localhost:5432/scada_mobile"
export SCADA_MOBILE_DATABASE_USERNAME="scada_user"
export SCADA_MOBILE_DATABASE_PASSWORD="scada_password"
export SCADA_MOBILE_JWT_ACCESS_SECRET="43567c4y59827cvn89cn7482y1837q23n8x4234r8974238c4857n49"
export SCADA_MOBILE_JWT_REFRESH_SECRET="4378v568590yftn7834vqcr78cyxr89c4xctg/54tc3uv98qc3n78="
```

---

## 6. Чек-лист перед Playwright-тестированием

- [ ] PostgreSQL запущена и доступна на `localhost:5432`
- [ ] БД `scada_mobile` существует
- [ ] Seed-данные применены (`scripts/seed_notifications.sql`)
- [ ] Backend запущен на `localhost:8080` (проверить `/api/v1/commands/health/live`)
- [ ] Frontend запущен на `localhost:5500` (проверить `curl -I http://localhost:5500`)
- [ ] Известны тестовые аккаунты (см. раздел 1)
- [ ] Пароль у всех тестовых пользователей: `password`

---

## 7. API endpoints для проверки (примеры)

### Health check
```bash
curl http://localhost:8080/api/v1/commands/health/live
```

### Аутентификация (получение токена)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"code":"USR-000001","password":"password"}'
```

### Список цехов (требует авторизации)
```bash
curl http://localhost:8080/api/v1/workshops \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

---

## 8. Примечания для нейроагентов

1. **Не используй `make` на Windows** — используй прямые команды из раздела 3.
2. **Всегда проверяй БД первой** — backend без БД не стартует.
3. **Пароль у всех тестовых пользователей одинаковый** — `password`.
4. **JWT-секреты для dev** можно использовать фиксированные (см. раздел 5).
5. **Swagger UI** доступен в dev-профиле: http://localhost:8080/swagger-ui.html
6. **Mock PrintSrv** включён в dev-профиле (`printsrv.mock.simulation-enabled: true`), ошибки на устройствах генерируются автоматически с вероятностью 1%.
