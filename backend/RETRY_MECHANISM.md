# 🔄 Retry-механизм для PrintSrv - Полная документация

## 📋 Описание

Архитектурно грамотный retry-механизм для автоматического опроса PrintSrv через Scheduler с **трехуровневой стратегией**
обработки ошибок, обеспечивающий высокую устойчивость к сбоям при полной прозрачности для клиентов REST API.

---

## 🎯 Ключевые возможности

- ✅ **Graceful Degradation** - клиенты получают последний валидный snapshot при сбоях
- ✅ **Progressive Retry** - от быстрых повторов до throttling в degraded mode
- ✅ **Exponential Backoff** - предотвращение "thundering herd"
- ✅ **Thread-Safety** - безопасная работа из нескольких потоков
- ✅ **Auto-Recovery** - автоматическое восстановление при появлении PrintSrv
- ✅ **Observability** - детальное логирование с эмодзи для быстрой диагностики

---

## 🏗️ Архитектура

### State Machine (Конечный автомат состояний)

```
┌─────────────────────────────┐
│  🟢 ШТАТНЫЙ РЕЖИМ           │
│  • Polling каждые 500ms     │
│  • consecutiveFailures = 0  │
│  • Snapshot актуален        │
└──────────┬──────────────────┘
           │
           │ 5 ошибок подряд
           ▼
┌─────────────────────────────┐
│  🟡 ПЕРЕПОДКЛЮЧЕНИЕ         │
│  • invalidate() socket      │
│  • 5 попыток с backoff      │
│  • 200ms → 3200ms           │
└──────────┬──────────────────┘
           │
           │ Все попытки исчерпаны
           ▼
┌─────────────────────────────┐
│  🔴 ВОССТАНОВЛЕНИЕ          │
│  • Проверка каждые 60 сек   │
│  • Старый snapshot доступен │
│  • Auto-recovery при успехе │
└─────────────────────────────┘
```

### Компоненты системы

#### 1. **SocketManager** (Connection Lifecycle)

- `getSocket()` - возвращает валидное соединение или создает новое
- `invalidate()` - принудительно закрывает socket
- Thread-safe через `synchronized` + `volatile`

#### 2. **ScadaDataPollingService** (Retry Logic)

- `pollPrintSrvState()` - основной метод с @Scheduled
- `handleReconnection()` - retry с экспоненциальным backoff
- `handleRecoveryMode()` - throttling в degraded mode

#### 3. **PrintSrvSnapshotStore** (In-Memory Cache)

- `AtomicReference<QueryAllResponseDTO>` - thread-safe storage
- Клиенты всегда получают данные (last valid snapshot)

---

## ⚙️ Конфигурация (application.yaml)

```yaml
printsrv:
    ip: 127.0.0.1
    port: 10101
    retry:
        max-attempts: 5                    # Попытки переподключения
        initial-delay-ms: 200              # Начальная задержка backoff
        max-delay-ms: 5000                 # Максимальная задержка (cap)
        recovery-check-interval-ms: 60000  # Интервал в recovery mode (60 сек)
    socket:
        connect-timeout-ms: 5000           # Таймаут на подключение
        read-timeout-ms: 5000              # Таймаут на чтение
```

**Exponential Backoff формула:**

```
delay = min(initial-delay-ms * 2^(attempt-1), max-delay-ms)

Попытка 1: 0ms
Попытка 2: 200ms   (200 * 2^1 = 400ms)
Попытка 3: 400ms   (200 * 2^2 = 800ms)
Попытка 4: 800ms   (200 * 2^3 = 1600ms)
Попытка 5: 1600ms  (200 * 2^4 = 3200ms)

Суммарное время: ~3 секунды
```

---

## 🔄 Режимы работы

### 🟢 Режим 1: Штатный (Normal Mode)

**Условия:** PrintSrv доступен, последний запрос успешен

**Поведение:**

- Опрос каждые 500ms
- При единичных ошибках - логирование и ожидание следующего цикла
- `consecutiveFailures` инкрементируется, но не достигает порога

**Логи:**

```
DEBUG: Starting PrintSrv polling cycle
DEBUG: Received snapshot from PrintSrv with 5 units
ERROR: ❌ Failed to poll PrintSrv (consecutive failures: 2)
DEBUG: Waiting for next polling cycle (in 500ms)...
```

---

### 🟡 Режим 2: Переподключение (Reconnection Mode)

**Триггер:** `consecutiveFailures >= 5`

**Алгоритм:**

1. Инвалидация текущего socket: `socketManager.invalidate()`
2. Retry loop с экспоненциальным backoff (5 попыток)
3. При успехе → штатный режим
4. При неудаче → режим восстановления

**Логи:**

```
WARN:  ⚠️ ERROR THRESHOLD REACHED (5 failures) - initiating reconnection
INFO:  🔄 Starting reconnection procedure...
WARN:  ⚠️ Invalidating socket connection to 127.0.0.1:10101
INFO:  🔌 Reconnection attempt 1/5 to PrintSrv...
ERROR: ❌ Reconnection attempt 1/5 failed: IOException
INFO:  ⏳ Waiting 400ms before retry attempt 2/5...
INFO:  🔌 Reconnection attempt 2/5 to PrintSrv...
INFO:  ✅ Reconnection successful on attempt 2/5
```

---

### 🔴 Режим 3: Восстановление (Recovery Mode)

**Условия:** Все 5 попыток переподключения исчерпаны

**Поведение:**

- `inRecoveryMode = true`
- Проверка доступности каждые 60 секунд (throttling)
- Клиенты продолжают получать устаревший snapshot
- При успешной проверке - выход из режима и возврат к штатному

**Логи:**

```
ERROR: 🚨 ENTERING RECOVERY MODE - all reconnection attempts failed.
       Will check PrintSrv availability every 60 seconds.
       Clients continue to use last valid snapshot.
INFO:  🔍 Recovery mode: checking PrintSrv availability...
ERROR: ❌ Recovery check failed: PrintSrv still unavailable (next check in 60s)
...
INFO:  🔍 Recovery mode: checking PrintSrv availability...
INFO:  ✅ PrintSrv is AVAILABLE again - exiting recovery mode
```

---

## 📊 Временные характеристики

| Событие                | Время                         |
|------------------------|-------------------------------|
| Обнаружение проблемы   | 2.5 сек (5 × 500ms)           |
| Переподключение (max)  | 3.0 сек (5 попыток с backoff) |
| Вход в recovery mode   | ~5.5 сек от первого сбоя      |
| Проверка в recovery    | 60 секунд                     |
| Восстановление (best)  | 500ms (следующий poll)        |
| Восстановление (worst) | 60 секунд (в recovery)        |

---

## 🎭 Гарантии для клиентов REST API / WebSocket

### GET `/api/workshops`, GET `/api/workshops/{id}/units`

- ✅ **Всегда возвращает 200 OK** (кроме первого запуска - 503)
- ✅ **Никогда не выбрасывает IOException** клиенту
- ✅ **Данные всегда доступны** (last valid snapshot)
- ⚠️ **Могут быть устаревшими** в режиме восстановления

### WebSocket `ws/alerts`

- Отправляет ALERT только при изменении состава ошибок (delta)

---

## 🔒 Thread-Safety

### Критические точки:

1. **SocketManager.socket** - `volatile Socket` + `synchronized` методы
2. **consecutiveFailures** - `AtomicInteger` (lock-free)
3. **inRecoveryMode** - `volatile boolean` (visibility)

### Гарантии:

- ✅ Нет race conditions при создании/закрытии socket
- ✅ Атомарные операции со счетчиками
- ✅ Видимость изменений между потоками

---

> Запуск приложения и команды управления — в [`README.md`](../README.md). Логи и их интерпретацию см. в [`LOGGING.md`](LOGGING.md).

## 🧪 Сценарии тестирования

### Тест 1: Штатная работа ✅

```
1. Запустите PrintSrv
2. Запустите Spring Backend
3. Наблюдайте логи каждые 500ms
4. Проверьте REST API - должен вернуть актуальные данные
```

### Тест 2: Кратковременный сбой (< 2.5 сек) ⚠️

```
1. Система работает в штатном режиме
2. Остановите PrintSrv на 2 секунды
3. Запустите PrintSrv обратно

Ожидаемые логи:
ERROR: ❌ Failed to poll PrintSrv (consecutive failures: 1-4)
INFO:  ✅ PrintSrv connection recovered after N consecutive failures

Результат: Автоматическое восстановление без переподключения
```

### Тест 3: Длительный сбой → Переподключение 🔄

```
1. Система работает в штатном режиме
2. Остановите PrintSrv на 5 секунд
3. Запустите PrintSrv обратно

Ожидаемые логи:
WARN:  ⚠️ ERROR THRESHOLD REACHED - initiating reconnection
INFO:  🔄 Starting reconnection procedure...
INFO:  🔌 Reconnection attempt 1/5...
INFO:  ✅ Reconnection successful on attempt N/5

Результат: Socket инвалидирован и создан заново
```

### Тест 4: PrintSrv недоступен → Recovery Mode 🚨

```
1. Система работает в штатном режиме
2. Остановите PrintSrv насовсем

Ожидаемые логи (через ~5.5 сек):
ERROR: 🚨 ENTERING RECOVERY MODE
INFO:  🔍 Recovery mode: checking PrintSrv availability...
ERROR: ❌ Recovery check failed (next check in 60s)

REST API (graceful degradation):
Invoke-RestMethod http://localhost:8080${scada.api.base-path}/health/ready
→ 200 OK (устаревший snapshot)

Результат: Graceful degradation, клиенты не видят ошибок
```

### Тест 5: Восстановление из Recovery Mode ✅

```
1. Доведите систему до Recovery Mode (тест 4)
2. Подождите до следующей проверки (до 60 сек)
3. Запустите PrintSrv перед проверкой

Ожидаемые логи:
INFO: 🔍 Recovery mode: checking PrintSrv availability...
INFO: ✅ PrintSrv is AVAILABLE again - exiting recovery mode

Результат: Автоматический возврат в штатный режим
```

---

## 📊 Эмодзи легенда для логов

| Эмодзи | Значение                            | Severity |
|--------|-------------------------------------|----------|
| ✅      | Успешная операция / восстановление  | INFO     |
| ❌      | Ошибка операции                     | ERROR    |
| ⚠️     | Предупреждение / достижение порога  | WARN     |
| 🔄     | Начало процесса переподключения     | INFO     |
| 🔌     | Попытка подключения                 | INFO     |
| ⏳      | Ожидание перед retry                | INFO     |
| 🚨     | Критическое событие (recovery mode) | ERROR    |
| 🔍     | Проверка доступности в recovery     | INFO     |

---

## 🐛 Troubleshooting

### Проблема: "PrintSrv snapshot not available yet"

**Симптомы:**

```
GET ${scada.api.base-path}/health/ready → 503 Service Unavailable
```

**Причина:** Приложение только запустилось, первый snapshot еще не загружен  
**Решение:** Подождите 1-2 секунды и повторите запрос

---

### Проблема: Система сразу входит в recovery mode

**Симптомы:**

```
ERROR: 🚨 ENTERING RECOVERY MODE (сразу после старта)
```

**Причина:** PrintSrv не запущен или недоступен  
**Решение:**

1. Проверьте, что PrintSrv запущен: `netstat -an | findstr 10101`
2. Проверьте конфигурацию `printsrv.host` и `printsrv.port`
3. Проверьте firewall настройки

---

### Проблема: Логи "Socket closed" при каждом запросе

**Симптомы:**

```
ERROR: Attempted to send request but socket is closed
```

**Причина:** SocketManager не может создать соединение  
**Решение:**

1. Убедитесь, что PrintSrv слушает на 127.0.0.1:10101
2. Проверьте firewall
3. Увеличьте таймауты в `application.yaml`

---

## 🔧 Настройка для разных окружений

### Development (быстрая отладка)

```yaml
printsrv:
    retry:
        max-attempts: 3
        initial-delay-ms: 100
        recovery-check-interval-ms: 10000  # 10 секунд
    socket:
        connect-timeout-ms: 2000
        read-timeout-ms: 2000

logging:
    level:
        dev.savushkin.scada.mobile.backend: TRACE  # Детальные логи
```

### Production (оптимизированная настройка)

```yaml
printsrv:
    retry:
        max-attempts: 5
        initial-delay-ms: 200
        recovery-check-interval-ms: 60000  # 60 секунд
    socket:
        connect-timeout-ms: 5000
        read-timeout-ms: 5000

logging:
    level:
        dev.savushkin.scada.mobile.backend: INFO  # Только важные события
```

---

## 📈 Метрики для мониторинга

### Ключевые показатели:

- `consecutiveFailures.get()` - текущее количество последовательных ошибок
- `inRecoveryMode` - флаг degraded состояния

### Алерты для production:

- `consecutiveFailures >= 3` → Warning (PrintSrv начинает "барахлить")
- `inRecoveryMode == true` → Critical (PrintSrv недоступен > 5 секунд)

---

## 🎓 Архитектурные принципы

✅ **Single Responsibility** - каждый компонент отвечает за одно  
✅ **Separation of Concerns** - retry отделен от connection management  
✅ **Configuration over Code** - все параметры в YAML  
✅ **Fail-Safe Defaults** - разумные значения по умолчанию  
✅ **Observability First** - детальное логирование  
✅ **Graceful Degradation** - система никогда не падает  
✅ **Thread-Safety** - synchronized + atomic + volatile  
✅ **Testability** - dependency injection, параметризация

---

## 📦 Измененные файлы

### Code changes (3 файла)

1. `SocketManager.java` - +60 строк (connection management + invalidation)
2. `ScadaDataPollingService.java` - +250 строк (retry logic + state machine)
3. `application.yaml` - +12 строк (configuration)

---

## 🔮 Будущие улучшения (опционально)

- [ ] Spring Boot Actuator health endpoint
- [ ] Prometheus metrics (consecutive failures, recovery mode)
- [ ] Grafana dashboard
- [ ] Circuit Breaker pattern (Resilience4j)
- [ ] Adaptive backoff на основе истории ошибок
- [ ] Алерты в Slack/Email при recovery mode

---

## ✅ Итог

**Production-ready retry-механизм** с:

- ✅ Трехуровневой стратегией обработки ошибок
- ✅ Экспоненциальным backoff (200ms → 3200ms)
- ✅ Graceful degradation для клиентов
- ✅ Thread-safety для concurrent доступа
- ✅ Детальной observability (эмодзи логи)
- ✅ Конфигурируемостью через YAML

**Готово к использованию!** 🚀

---

**Реализовано:** GitHub Copilot  
**Дата:** 2026-02-08  
**Версия:** 2.0 (обновлен initial-delay-ms: 200)  
**Статус:** ✅ PRODUCTION READY
