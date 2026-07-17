# Техническое задание: Gateway → Kafka

> **Для:** Якимовец Е. (Gateway)  
> **От:** Логинов Г. (Mobile Srv)  
> **Назначение:** Определить формат данных, которые Gateway публикует в Kafka

## Ревизии

| Версия | Дата | Автор | Изменения |
|---|---|---|---|
| 1.0 | 2026-07-16 | Логинов Г. | Начальная версия |

---

## 1. Архитектура

```
Gateway ← Kafka (config) ← Mobile Svr (админ-панель)
   │
   │ TCP (P001-фрейм, windows-1251)
   ▼
PrintServer (по одному на каждый автомат)
   │
   │ сырые данные
   ▼
Gateway (агрегация)
   │
   │ Kafka Producer
   ▼
Kafka (топики scada.mobile.*)
   │
   │ Kafka Consumer
   ▼
Mobile Svr
```

**Принцип:** Gateway и Mobile Svr не имеют прямой связи. Только Kafka.

---

## 2. Конфигурация автоматов (чтение из Kafka)

### 2.1. Топик

`scada.mobile.config.unit-changes` — Gateway подписывается как Consumer (group: `scada-mobile-gateway`).

### 2.2. Формат сообщений

**UNIT_UPSERT** — создание или обновление автомата:

```json
{
  "eventType": "UNIT_UPSERT",
  "instanceId": "trepko1",
  "host": "192.168.1.10",
  "port": 9100,
  "displayName": "Trepko №1",
  "workshopId": 1,
  "isActive": true,
  "timestamp": "2026-07-16T10:30:00Z"
}
```

**UNIT_DELETE** — удаление автомата:

```json
{
  "eventType": "UNIT_DELETE",
  "instanceId": "trepko1",
  "timestamp": "2026-07-16T10:30:00Z"
}
```

### 2.3. Поведение Gateway

| Событие | Действие |
|---|---|
| `UNIT_UPSERT`, новый `instanceId` | Начать опрос нового автомата |
| `UNIT_UPSERT`, изменён `host`/`port` | Переподключиться по новому адресу |
| `UNIT_UPSERT`, `isActive: false` | Прекратить опрос |
| `UNIT_DELETE` | Прекратить опрос, освободить ресурсы |
| Старт Gateway | Прочитать все непрочитанные сообщения (восстановить конфигурацию) |

---

## 3. Протокол PrintServer

### 3.1. Транспорт

| Параметр | Значение |
|---|---|
| Протокол | TCP |
| Хост:порт | Из конфигурации Kafka (поля `host`, `port`) |
| Кодировка | **windows-1251** |
| Макс. размер ответа | 10 МБ |

### 3.2. TCP-фрейм

```
┌─────────┬──────────┬──────────┐
│  MAGIC  │  LENGTH  │ JSON_DATA│
│ 4 байта │ 4 байта  │ N байт   │
│  P001   │ BigEndian│windows-1251
└─────────┴──────────┴──────────┘
```

### 3.3. Команды

**QueryAll** — чтение всех тегов устройства:

Запрос:
```json
{"DeviceName": "Line", "Command": "QueryAll"}
```

Ответ:
```json
{
  "DeviceName": "Line",
  "Command": "QueryAll",
  "Units": {
    "u1": {
      "Properties": {
        "ST": "0",
        "Level1Printers": "Printer11,Printer12",
        "Level1Cams": "CamChecker1,CamAgregation1",
        "LineDevices": "CamChecker1,Printer11,Printer12"
      }
    }
  }
}
```

**Важно:** `SetUnitVars` пока не нужен — только чтение.

---

## 4. Определение состава устройств

### 4.1. Алгоритм

1. Опросить `Line` (QueryAll)
2. Из Properties извлечь списки (через запятую):
   - `LineDevices` — основной список
   - `Level1Printers`, `Level2Printers` — принтеры
   - `Level1Cams`, `Level2Cams`, `SignalCams` — камеры
3. Объединить все имена в единый список
4. Опросить: `Line`, `scada`, `BatchQueue` + все обнаруженные периферийные устройства

### 4.2. Пример

```
Line.LineDevices = "CamChecker1,CamAgregation1,Printer11,Printer12"
→ Опросить: Line, scada, BatchQueue, CamChecker1, CamAgregation1, Printer11, Printer12
```

---

## 5. Топики и форматы сообщений в Kafka

Naming: `scada.mobile.{scope}.{id}.{type}`

### 5.1. `scada.mobile.unit.{instanceId}.line-status`

Данные партии (вкладка «Партия»).

```json
{
  "lineName": "Линия розлива ПЭТ №2",
  "lineState": "1",
  "shortCode": "198",
  "description": "Продукт XYZ",
  "ean13": "4601234567890",
  "batchNumber": "198",
  "dateProduced": "10.01.2026",
  "datePacking": "15.01.2026",
  "dateExpiration": "10.07.2026",
  "initialCounter": "1605 | 147 | 19.08.2025",
  "site": "Брест",
  "itf": "14601234567897",
  "capacity": "20",
  "boxCount": "100",
  "packageCount": "2000",
  "freeze": "0",
  "region": "BY",
  "design": "42",
  "printDM": "1",
  "cameraRead": "1950",
  "cameraUnread": "50"
}
```

**Правила:**
- `lineState` ← `Line.ST`
- Поля партии: `BatchQueue` (приоритет) → `Printer11` (fallback)
- `initialCounter` ← `Printer11.CurItem`
- `cameraRead`/`cameraUnread` — агрегация камер (3 фазы, см. раздел 7.1)

### 5.2. `scada.mobile.unit.{instanceId}.devices-status`

Сырые снапшоты всех устройств.

```json
{
  "printers": [
    {
      "deviceName": "Printer11",
      "st": "0",
      "error": "0",
      "batch": "1605 | 147 | 19.08.2025",
      "disconnected": false
    }
  ],
  "aggregationCams": [
    {
      "deviceName": "CamAgregation1",
      "read": "975",
      "unread": "25",
      "st": "0",
      "error": "0",
      "disconnected": false
    }
  ],
  "aggregationBoxCams": [
    {
      "deviceName": "CamAgregationBox1",
      "read": "500",
      "unread": "0",
      "st": "0",
      "error": "0",
      "disconnected": false
    }
  ],
  "checkerCams": [
    {
      "deviceName": "CamChecker1",
      "read": "1950",
      "unread": "50",
      "st": "0",
      "error": "0",
      "disconnected": false
    }
  ]
}
```

**Классификация по имени:**
- `Printer*` → `printers`
- `CamAgregationBox*` → `aggregationBoxCams`
- `CamAgregation*` → `aggregationCams`
- `Cam*` → `checkerCams`

**Поля:**
- `st` ← `PrinterXX.ST` (fallback: `scada.LineDev0{NN}ST`)
- `error` ← `PrinterXX.Error`
- `batch` ← `PrinterXX.CurItem`
- `read` ← `CamXX.Total` (fallback: `scada.Dev0{41+i*2}CounterGeneral`)
- `unread` ← `CamXX.Failed` (fallback: `scada.Dev0{41+i*2}CounterMissing`)
- `disconnected` — true, если устройство не ответило на QueryAll

### 5.3. `scada.mobile.unit.{instanceId}.queue`

Очередь партий.

```json
{
  "items": [
    {"position": 1, "shortCode": "198", "batch": "198", "dateProduced": "10.01.2026"},
    {"position": 2, "shortCode": "199", "batch": "199", "dateProduced": "11.01.2026"}
  ]
}
```

**Правила:** `Item01`–`Item10` из `BatchQueue`, формат `"Описание | номер партии | дата выработки"`, пропускать `"Пусто"` и пустые.

### 5.4. `scada.mobile.unit.{instanceId}.errors`

Активные ошибки.

```json
{
  "deviceErrors": [
    {"deviceName": "LineDev041", "property": "LineDev041Connection", "value": "1", "description": "Нет связи с устройством"},
    {"deviceName": "LineDev041", "property": "LineDev041Fail", "value": "1", "description": "Нет кодов маркировки"}
  ],
  "lineErrors": []
}
```

**Правила:** из `scada` Properties, ключи с суффиксами: `Connection`, `Fail`, `Dublicate`, `DiffEan`, `Work`, `Data`, `Batch`, `Error`. Активны если значение ≠ `"0"`.

### 5.5. `scada.mobile.unit.{instanceId}.alert`

Дельта алертов — **только при изменении**.

Появление:
```json
{"active": true, "errors": [{"device": "Dev041", "code": 0, "message": "Нет связи с устройством"}]}
```

Снятие:
```json
{"active": false, "errors": []}
```

### 5.6. `scada.mobile.workshop.{workshopId}.units-status`

Сводный статус цеха.

```json
[
  {"unitId": "trepko1", "workshopId": 1, "event": "В работе", "cameraRead": "1950", "cameraUnread": "50"},
  {"unitId": "trepko2", "workshopId": 1, "event": "Остановка\nLineDev011: Нет связи", "cameraRead": "0", "cameraUnread": "0"}
]
```

---

## 6. Sсada-ключи (справочно)

| Устройство | Scada-ключ |
|---|---|
| Aggregation cam[i] | `Dev{41+i*2}` (Dev041, Dev043…) |
| AggregationBox cam[i] | `Dev{42+i*2}` (Dev042, Dev044…) |
| EAN-checker | `Dev{70+N}` (Dev071, Dev072…) |
| Printer11 | `LineDev011`, `LineDev11` |
| Printer12 | `LineDev012`, `LineDev12` |

---

## 7. Алгоритмы агрегации

### 7.1. Счётчики камер (cameraRead / cameraUnread)

**Вход:** все камеры (aggregation + aggregationBox + checker, без EAN-чекеров).

1. Найти первую камеру с ненулевым `read` → вернуть (read, unread)
2. Если все read=0 — найти первую с ненулевым `unread` → вернуть (read, unread)
3. Иначе → `("0", "0")`

### 7.2. Дельта алертов

1. Сохранить текущий список ошибок
2. При следующем цикле сформировать новый список
3. Публиковать в Kafka **только если списки различаются**

---

## 8. Параметры Kafka

| Параметр | Значение |
|---|---|
| Consumer Group (config) | `scada-mobile-gateway` |
| Формат | JSON, UTF-8 |
| Partition key | `instanceId` |
| Retention (data) | 24 часа |
| Retention (config) | 7 дней |
