# ТЗ: Миграция Mobile Svr с TCP-polling на Kafka

> **Для:** Логинов Г.  
> **Суть:** Заменить модуль прямого TCP-опроса PrintServer на модуль чтения из Kafka. Gateway (Якимовец) берёт опрос на себя.

---

## Ревизии

| Версия | Дата | Автор | Изменения |
|---|---|---|---|
| 1.0 | 2026-07-16 | Логинов Г. | Начальная версия |

---

## 1. Принцип

```
ДО:  TcpPrintSrvClient → PrintSrvInstancePoller → UnitDetailService → WS
ПОСЛЕ: Kafka Consumer → UnitDetailService → WS
                ↑
         Gateway (Женя) опрашивает PrintServer
```

**Что меняется:** источник данных — с TCP-polling на Kafka Consumer.  
**Что НЕ меняется:** фронтенд, WebSocket-форматы, БД, админ-панель, логика агрегации.

---

## 2. Что удалить

### 2.1. Классы (полное удаление)

| Класс | Путь | Почему |
|---|---|---|
| `TcpPrintSrvClient` | `infrastructure/integration/printsrv/client/` | TCP-соединение больше не нужно |
| `PrintSrvInstancePoller` | `infrastructure/polling/` | Polling больше не нужен |

### 2.2. Части классов (удалить методы/поля)

| Класс | Что удалить | Почему |
|---|---|---|
| `PrintSrvClient` (интерфейс) | Весь интерфейс или оставить как маркер | Больше нет TCP-клиента |
| `PrintSrvConfig` | Поля `host`, `port` из `@ConfigurationProperties` | Хост и порт теперь в БД, передаются через Kafka |
| `application.yaml` | Секция `scada.printsrv.*` | Не нужна, Gateway читает из Kafka |

---

## 3. Что создать

### 3.1. Kafka Config Producer — публикация конфигурации

**Назначение:** при изменении автомата в админ-панели публиковать `UNIT_UPSERT` / `UNIT_DELETE` в `scada.mobile.config.unit-changes`.

**Где:** `infrastructure/kafka/config/UnitConfigKafkaProducer.java`

**Когда публиковать:**
- Создание автомата (`UnitService.create`)
- Обновление автомата (`UnitService.update`) — если изменились `printsrv_host`, `printsrv_port`, `is_active`, список устройств
- Удаление автомата (`UnitService.delete`)
- При старте приложения — для всех `is_active = true`

**Формат:** см. ТЗ Gateway (раздел 2.2). Тебе нужны поля: `eventType`, `instanceId`, `host`, `port`, `displayName`, `workshopId`, `isActive`, `timestamp`.

### 3.2. Kafka Consumer — чтение данных

**Назначение:** читать агрегированные данные из `scada.mobile.unit.*` и `scada.mobile.workshop.*`.

**Где:** `infrastructure/kafka/ScadaDataKafkaConsumer.java`

**Топики:**
- `scada.mobile.unit.{instanceId}.line-status`
- `scada.mobile.unit.{instanceId}.devices-status`
- `scada.mobile.unit.{instanceId}.queue`
- `scada.mobile.unit.{instanceId}.errors`
- `scada.mobile.unit.{instanceId}.alert`
- `scada.mobile.workshop.{workshopId}.units-status`

**Group ID:** `scada-mobile-backend`

**Что делать с сообщением:** вызвать существующий `StatusBroadcaster` с данными из Kafka.

---

## 4. Что адаптировать

### 4.1. DeviceAutoDiscoveryService — смена источника данных

**Сейчас:** получает runtime-снапшот `Line` через TCP, сравнивает с `unit_devices` в БД.

**Нужно:** получать `devices-status` из Kafka, сравнивать с `unit_devices` в БД.

**Что менять:**
- Метод `syncRuntimeDevices(PrintSrvInstance instance, DeviceSnapshot lineSnapshot)` → `syncKafkaDevices(String instanceId, DevicesStatusMessageDTO devicesStatus)`
- Вместо `lineSnapshot.rawProperties.get("LineDevices")` — список `deviceName` из `devicesStatus`
- Логика сравнения (`DeviceCompositionService.compareWithRuntime`) — **не трогать**, только переименовать метод на `compareWithKafka`
- Уведомления админа — оставить как есть

### 4.2. StatusBroadcaster — смена источника данных

**Сейчас:** `PrintSrvInstancePoller` вызывает `broadcast()` после каждого цикла опроса.

**Нужно:** Kafka Consumer вызывает `broadcast()` при получении сообщения.

**Что менять:**
- Убрать вызовы из `PrintSrvInstancePoller` (он удаляется)
- Добавить вызовы из Kafka Consumer
- Порядок обработки сообщений для одного автомата:
  1. `devices-status` → сохранить в кэш, вызвать `DeviceAutoDiscoveryService.syncKafkaDevices()`
  2. `line-status` → вызвать `UnitDetailService.buildLineStatus()`, broadcast `LINE_STATUS`
  3. `queue` → broadcast `QUEUE`
  4. `errors` → broadcast `ERRORS`
  5. `alert` → broadcast `ALERT` (если `active: true`)

### 4.3. UnitDetailService — сохранить почти полностью

**Что НЕ менять:**
- Логика агрегации `LINE_STATUS` (BatchQueue-first, Printer-fallback)
- Логика `DEVICES_STATUS`
- Логика `QUEUE`
- Логика `ERRORS`
- Все DTO (`LineStatusMessageDTO`, `DevicesStatusMessageDTO`, `QueueMessageDTO`, `AlertMessageDTO`)

**Что менять:**
- Источник данных: вместо `Map<String, DeviceSnapshot> allDeviceSnapshots` (из poller) — данные из Kafka-сообщений
- Метод `buildLineStatus(String instanceId, LineStatusKafkaDTO lineStatus)` — адаптировать под формат из Kafka
- Метод `buildDevicesStatus(String instanceId, DevicesStatusKafkaDTO devicesStatus)` — адаптировать под формат из Kafka

### 4.4. AlertService — сохранить полностью

**Не менять.** Источник данных (`ERRORS`) тот же, только приходит из Kafka вместо TCP. Логика дельты неизменна.

### 4.5. WorkshopService — адаптировать источник

**Сейчас:** агрегирует данные из runtime-снапшотов всех автоматов.

**Нужно:** агрегировать данные из `scada.mobile.workshop.{id}.units-status` (Gateway уже агрегировал).

**Вариант:** либо читать готовый `units-status` из Kafka, либо строить самостоятельно из `line-status` каждого автомата. Рекомендую **читать готовый** из Kafka — меньше кода.

### 4.6. UnitController — добавить публикацию в Kafka

**Сейчас:** `POST /api/units`, `PUT /api/units/{id}`, `DELETE /api/units/{id}` — только CRUD в БД.

**Нужно:** после успешного CRUD-операции вызвать `UnitConfigKafkaProducer`.

**Пример:**
```java
@PostMapping
public ResponseEntity<UnitDTO> create(@RequestBody UnitCreateDTO dto) {
    UnitDTO created = unitService.create(dto);
    unitConfigKafkaProducer.publishUnitUpsert(created); // ← добавить
    return ResponseEntity.ok(created);
}
```

### 4.7. ScadaKeyMapper — сохранить полностью

Маппинг scada-ключей (`LineDev011` → `Printer11`, `Dev041` → `CamAgregation1`) — неизменен.

### 4.8. PrintSrvMapper — пересмотреть

**Сейчас:** маппит `QueryAllResponseDTO` → `DeviceSnapshot`.

**Нужно:** Gateway теперь делает это сам. Тебе данные приходят уже в формате `DevicesStatusMessageDTO`.

**Решение:** удалить или оставить для backward compatibility. Если `DeviceSnapshot` используется в `DeviceAutoDiscoveryService` — оставить, адаптировать маппинг из Kafka-DTO в `DeviceSnapshot`.

---

## 5. Порядок действий

### Этап 1: Подготовка
1. Добавить зависимость `spring-kafka` в `pom.xml`
2. Настроить `KafkaProperties` в `application.yaml` (bootstrap-servers, consumer/producer config)

### Этап 2: Публикация конфигурации
3. Создать `UnitConfigKafkaProducer`
4. В `UnitController.create/update/delete` добавить вызов producer
5. При старте приложения публиковать `UNIT_UPSERT` для всех активных автоматов

### Этап 3: Чтение данных
6. Создать DTO для Kafka-сообщений (или переиспользовать существующие WS-DTO)
7. Создать `ScadaDataKafkaConsumer`
8. В consumer вызывать `StatusBroadcaster` с полученными данными

### Этап 4: Адаптация сервисов
9. Адаптировать `DeviceAutoDiscoveryService` — источник: Kafka
10. Адаптировать `StatusBroadcaster` — вызов из Kafka Consumer
11. Адаптировать `UnitDetailService` — источник: Kafka
12. Адаптировать `WorkshopService` — источник: Kafka

### Этап 5: Удаление старого
13. Удалить `TcpPrintSrvClient`
14. Удалить `PrintSrvInstancePoller`
15. Удалить `PrintSrvClient` интерфейс (если не нужен)
16. Очистить `application.yaml` от `scada.printsrv.*`

### Этап 6: Тестирование
17. Интеграционное тестирование с Gateway

---

## 6. Сводная таблица: что с каким классом

| Класс | Действие | Комментарий |
|---|---|---|
| `TcpPrintSrvClient` | **Удалить** | TCP больше не нужен |
| `PrintSrvInstancePoller` | **Удалить** | Polling больше не нужен |
| `PrintSrvClient` (interface) | **Удалить** | Нет реализации |
| `PrintSrvConfig` | **Адаптировать** | Убрать host/port |
| `PrintSrvMapper` | **Адаптировать** | Маппинг из Kafka-DTO в `DeviceSnapshot` |
| `UnitDetailService` | **Адаптировать** | Источник: Kafka вместо TCP |
| `AlertService` | **Не трогать** | Логика неизменна |
| `DeviceAutoDiscoveryService` | **Адаптировать** | Источник: Kafka вместо runtime |
| `DeviceCompositionService` | **Не трогать** | Логика сравнения неизменна |
| `StatusBroadcaster` | **Адаптировать** | Вызывать из Kafka Consumer |
| `WorkshopService` | **Адаптировать** | Источник: Kafka |
| `ScadaKeyMapper` | **Не трогать** | Маппинг ключей неизменен |
| `UnitController` | **Адаптировать** | Добавить публикацию в Kafka |
| `UnitService` | **Не трогать** | CRUD неизменен |
| DTO (`LineStatusMessageDTO` и др.) | **Не трогать** | Форматы WS неизменны |
| `UnitConfigKafkaProducer` | **Создать** | Публикация конфигурации |
| `ScadaDataKafkaConsumer` | **Создать** | Чтение данных из Kafka |

---

## 7. Важные моменты

**Фронтенд:** ничего не меняется. WebSocket-форматы остаются прежними.

**Админ-панель:** ничего не меняется. CRUD автоматов остается прежним, только добавляется публикация в Kafka.

**БД:** ничего не меняется. Таблицы `units`, `device_catalog`, `unit_devices` — без изменений.

**DeviceAutoDiscovery:** логика сравнения неизменна. Меняется только источник данных: вместо runtime-снапшота `Line` — `devices-status` из Kafka.

**Gateway и ты:** не общаетесь напрямую. Только через Kafka.
