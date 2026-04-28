# Scan Cycle Architecture Implementation Checklist

> **Цель:** Изолировать клиентов от PrintSrv через snapshot с автономным циклом read-only опроса.

> **Архитектурный принцип:** Один поток последовательно выполняет: READ из PrintSrv → UPDATE snapshot → PUSH WebSocket-алёртов
> при изменении состояния ошибок. Запись в PrintSrv (`SetUnitVars`) в данном проекте не применяется. Это исключает race conditions и соответствует промышленным стандартам SCADA.

---

## 🏗️ Архитектурная диаграмма

```
┌──────────────────────────────────────────────────────┐
│  ЕДИНЫЙ ЦИКЛ СКАНИРОВАНИЯ (каждые 5 секунд)          │
└──────────────────────────────────────────────────────┘
                    ↓
        ┌───────────────────────┐
        │ [1] READ из PrintSrv  │ ← QueryAllCommand
        └───────────────────────┘
                    ↓
        ┌───────────────────────┐
        │ [2] UPDATE snapshot   │ ← PrintSrvSnapshotStore
        └───────────────────────┘
                    ↓
        ┌───────────────────────────────┐
        │ [3] PUSH алёрты (при дельте)  │ ← WebSocket (ws/alerts)
        └───────────────────────────────┘
                    ↓
              ← Повтор цикла

┌──────────────────────────────────┐
│ Клиент GET /api/workshops        │ → Читает конфиг цехов/аппаратов
└──────────────────────────────────┘

┌──────────────────────────────────┐
│ Клиент WebSocket /ws/unit/{id}   │ → Получает поток данных аппарата
└──────────────────────────────────┘
```

---

## 📋 Фаза 2: Модификация ScadaDataPollingService

### ✅ Задача 2.1: Внедрить зависимости

**Файл:** `DevScanCycleScheduler.java` (dev) / `PrintSrvPollingScheduler.java` (prod)

**Конструктор содержит:**

```java
private final PrintSrvClientRegistry registry;
private final PrintSrvMapper mapper;
private final DeviceSnapshotWriter snapshotWriter;
```

---

### ✅ Задача 2.2: Реализовать метод `scanCycle()`

**Логика метода `scanCycle()`:**

```java
@Scheduled(fixedDelayString = "${printsrv.polling.fixed-delay-ms:5000}")
public void scanCycle() {
    for (String instanceId : registry.getInstanceIds()) {
        PrintSrvClient client = registry.get(instanceId);
        if (!client.isAlive()) continue;
        try {
            // [1] READ из PrintSrv
            QueryAllResponseDTO dto = client.queryAll("Line");

            // [2] UPDATE snapshot
            DeviceSnapshot snapshot = mapper.toDomainDeviceSnapshot(dto);
            snapshotWriter.save(snapshot);
        } catch (Exception e) {
            // Логировать ошибку чтения PrintSrv
            // Snapshot не обновляется (клиенты получат устаревшие данные)
        }
    }
}
```

---

## 📋 Фаза 4: Обработка ошибок и граничные случаи

### ✅ Задача 4.1: Обработка недоступности PrintSrv

**Файл:** `DevScanCycleScheduler.java` / `PrintSrvPollingScheduler.java`

**Логика при ошибке в `scanCycle()`:**

```java
} catch (Exception e) {
    // READ failed - PrintSrv недоступен
    // Snapshot НЕ обновляется → клиенты получают stale data
    // Логировать: "PrintSrv недоступен, повтор через N секунд"
}
```

**Важно:**

- Snapshot остаётся последним валидным (graceful degradation)
- Scheduler продолжает работу — следующий цикл повторит попытку

---

## 📋 Фаза 5: Thread Safety и синхронизация

### ✅ Задача 5.1: Анализ потоков

**Потоки в системе:**

1. **WebSocket Session Threads** (Spring) — отправка алёртов клиентам
2. **Scheduler Thread** — выполняет `scanCycle()` каждые N секунд

**Точки конкуренции:**

- `PrintSrvSnapshotStore.save()` — запись из Scheduler
- WebSocket-рассылка (чтение из snapshot) — одновременно с записью

**Решение:** `AtomicReference` в `PrintSrvSnapshotStore` обеспечивает thread-safety

---

### ✅ Задача 5.2: Гарантии безопасности

**PrintSrvSnapshotStore:**

- ✅ Thread-safe (AtomicReference)
- ✅ Используется только Scheduler для записи
- ✅ Используется WebSocket threads для чтения (immutable snapshot)

**Нет необходимости в:**

- ❌ Блокировках (locks)
- ❌ Синхронизации методов
- ❌ Volatile полях (используется AtomicReference)

---

## 📋 Тестирование

### ✅ Тест 1: Опрос всех экземпляров (dev-профиль)

**Шаги:**

1. Запустить приложение с `--spring.profiles.active=dev`
2. Дождаться лога: `DevScanCycleScheduler: Snapshot saved from instance 'trepko1'`
3. Отправить: `GET http://localhost:8080${scada.api.base-path}/health/ready`
4. **Ожидание:** `{ "ready": true }`

**Критерии успеха:**

- ✅ `DevScanCycleScheduler` сохраняет snapshot каждые 5 секунд
- ✅ `MockStateSimulator` флипает ошибки на аппаратах
- ✅ Health endpoints отвечают корректно

---

### ✅ Тест 2: Недоступность экземпляра PrintSrv (mock офлайн)

**Шаги:**

1. Запустить приложение (dev)
2. Через `MockPrintSrvClientRegistry` пометить `trepko1` как `alive=false`
3. Дождаться scan cycle
4. Проверить логи: `isAlive() = false, пропускаем экземпляр`
5. Snapshot остаётся последним валидным

**Критерии успеха:**

- ✅ Snapshot не обновляется, пока экземпляр офлайн
- ✅ При возвращении онлайн snapshot актуализируется
- ✅ Старый snapshot возвращается (graceful degradation)

---

## 🎯 Критерии готовности

### Функциональные требования:

- ✅ Scan cycle читает snapshot из PrintSrv через QueryAll
- ✅ `GET /api/workshops` и `GET /api/workshops/{id}/units` возвращают данные из snapshot
- ✅ WebSocket `ws/unit/{id}` пушит данные после каждого цикла
- ✅ WebSocket `ws/alerts` отправляет ALERT только при изменении состава ошибок
- ✅ При недоступности PrintSrv snapshot остаётся последним валидным

### Нефункциональные требования:

- ✅ Нет race conditions (один поток для scan cycle)
- ✅ Thread-safe snapshot (AtomicReference)
- ✅ Graceful degradation при ошибках PrintSrv
- ✅ Логирование всех критических событий
- ✅ Нет memory leaks (snapshot immutable, буфера записи нет)

---

## 📝 Порядок реализации

**День 1:**

- Фаза 2: DevScanCycleScheduler — scan cycle READ → UPDATE
- Фаза 3: Обработка ошибок PrintSrv

**День 2:**

- Фаза 4: Обработка ошибок + Thread Safety
- Тестирование

**День 3:**

- Реализация REST + WebSocket эндпоинтов (Цеха, Аппараты)
- Реализация пуша алёртов (детектор изменений + `ws/alerts`)

**День 4:**

- Логирование (логгеры SLF4J)
- Документация (JavaDoc)
- Code Review

---

## ⚠️ Архитектурные решения и компромиссы

### ✅ Преимущества Scan Cycle подхода:

1. **Простота:** Один поток, линейная логика, нет сложной синхронизации
2. **Надежность:** Исключены race conditions по дизайну
3. **Прогнозируемость:** Четкий цикл чтение→логика→запись
4. **Соответствие стандартам:** Классический паттерн для PLC/SCADA систем
5. **Debuggability:** Легко отследить поток данных

### ⚠️ Компромиссы:

1. **Задержка видимости изменений:** До 1 секунды (клиент получает ответ мгновенно, но изменения в `GET /query-all`
   видны после следующего цикла)
    - **Митигация:** Приемлемо для мобильных приложений (пользователь не замечает)
    - **Текущая настройка:** В рабочих профилях используется цикл 1000ms
    - **Улучшение:** Можно снизить до 500ms, если нужна еще более быстрая синхронизация

2. **Потеря команд при ошибке:** Нет retry-механизма
    - **Митигация:** Клиент может повторить запрос по таймауту или показать "повторить"

3. **Ограничение throughput:** Максимум 100 команд за 1 секунду = 100 RPS
    - **Митигация:** Более чем достаточно для мобильных клиентов

4. **Eventual Consistency:** Snapshot может отставать от реальности на ≤1 секунду
    - **Митигация:** PrintSrv = источник правды, snapshot догоняет каждую секунду

### 🎯 Почему это правильное решение:

- **Требование руководителя:** "Чтение и запись в одном потоке последовательно"
- **Промышленный стандарт:** PLC Scan Cycle используется в Siemens, Rockwell, Schneider
- **Безопасность:** Нет гонок, нет deadlocks, нет сложной синхронизации
- **Масштаб проекта:** Мобильное приложение, не высоконагруженная система

---

## 🔧 Полезные команды

**Проверить health:**

```powershell
Invoke-RestMethod http://localhost:8080${scada.api.base-path}/health/live
Invoke-RestMethod http://localhost:8080${scada.api.base-path}/health/ready
```

**Мониторинг scan cycle в логах:**

```powershell
Get-Content -Path "logs/spring.log" -Wait | Select-String -Pattern "scanCycle|Snapshot saved"
```

---

## 📚 Дополнительные материалы

**Для понимания концепции:**

- PLC Scan Cycle: https://www.plcacademy.com/plc-scan-cycle/
- WebSocket Protocol: RFC 6455

---

**Версия:** 3.0 (Read-Only Scan Cycle + WebSocket Push)  
**Дата:** 01.03.2026  
**Автор:** Architecture Design для SCADA Mobile Backend
