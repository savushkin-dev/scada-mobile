# Документация API PrintSrv

> **Документация составлена на основе реверс-инжиниринга сервера MarkPrintServer.exe и тестирования протокола.**
> Последнее обновление: 1 февраля 2026 г.

## Общая информация

**PrintSrv (MarkPrintServer)** — сервер маркировки, источник данных для SCADA-системы.
Написан на .NET Framework 4.8+, использует IronPython для скриптов обработки.

## Подключение

| Параметр | Значение |
|----------|----------|
| IP-адрес | `127.0.0.1` (локально) или IP сервера |
| Порт | `10101` |
| Протокол | TCP сокеты |
| Кодировка | **windows-1251** (НЕ UTF-8!) |

---

## Протокол: формат фрейма

Каждое сообщение (запрос и ответ) передается в виде фрейма:

```
[MAGIC][LENGTH][JSON_DATA]
  4б     4б      N байт
```

| Поле | Размер | Описание |
|------|--------|----------|
| MAGIC | 4 байта | Строка `P001` (ASCII) |
| LENGTH | 4 байта | Длина JSON в байтах (Big Endian, int32) |
| JSON_DATA | LENGTH байт | JSON в кодировке windows-1251 |

### Пример в hex

```
50 30 30 31  00 00 00 2A  7B 22 44 65 76 69 63 65 ...
├─ P001 ──┤  ├─ 42 ────┤  ├─ {"Device... ─────────
   MAGIC      LENGTH         JSON DATA
```

---

## Иерархия данных

```
Device (устройство, например "Line")
  └── Unit (юнит, нумерация с 1: Unit=1 → "u1")
       └── Properties (теги: "command", "message", "Error", ...)
```

**Важно про нумерацию Unit:**

- В запросах: `"Unit": 1` (целое число, 1-based!)
- В ответах: `"u1"` (строка с префиксом "u")
- Unit=1 → u1, Unit=2 → u2, и т.д.

---

## Доступные команды

⚠️ **Сервер поддерживает ТОЛЬКО 2 команды:**

| Команда | Описание |
|---------|----------|
| `QueryAll` | Чтение ВСЕХ тегов устройства |
| `SetUnitVars` | Установка значений тегов юнита |

> Команды типа `GetUnitVars`, `QueryUnit`, `GetUnit` и т.п. **НЕ существуют** — сервер просто не отвечает на них (таймаут).

---

### 1. QueryAll — Чтение всех тегов

**Запрос:**

```json
{
  "DeviceName": "Line",
  "Command": "QueryAll"
}
```

**Ответ (реальный пример):**

```json
{
  "DeviceName": "Line",
  "Command": "QueryAll",
  "Units": {
    "u1": {
      "State": "",
      "Task": "",
      "Counter": 0,
      "Properties": {
        "command": "766",
        "Error": "0",
        "ErrorMessage": "",
        "CurItem": "1605 | 147 | 19.08.2025",
        "message": "766",
        "batchIdCodesQueue": "0",
        "setBatchID": "0",
        "devChangeBatch": "",
        "devType": "10",
        "batchId": "",
        "devsChangeBatchIDQueueControl": "",
        "cmdsuccess": "0",
        "ST": "0",
        "enableErrors": "0",
        "OnChangeBatchPrinters": "Level1Printers",
        "Level1Printers": "Printer11,Printer12,Printer13,Printer14",
        "Level2Printers": "Printer2",
        "OnChangeBatchCams": "Level1Cams",
        "Level1Cams": "CamChecker1,CamChecker2,CamAgregation1,CamAgregation2",
        "Level2Cams": "",
        "SignalCams": "",
        "LineDevices": "CamChecker1,CamChecker2,CamAgregation1,CamAgregationBox1,CamAgregationBox2,Printer11,Printer12,Printer13,Printer14",
        "LineID": "5"
      }
    }
  }
}
```

**Особенности:**

- Возвращает ВСЕ юниты со ВСЕМИ тегами
- Нет возможности запросить только один юнит (такой команды нет)

---

### 2. SetUnitVars — Установка значений тегов

**Запрос:**

```json
{
  "DeviceName": "Line",
  "Unit": 1,
  "Command": "SetUnitVars",
  "Parameters": {
    "command": "555"
  }
}
```

⚠️ **КРИТИЧЕСКИ ВАЖНО:**

- `Unit` — **целое число** (1, 2, 3...), НЕ строка "u1"!
- Нумерация **1-based**: Unit=1 соответствует юниту "u1"
- Можно передавать несколько параметров сразу

**Ответ (реальный пример):**

```json
{
  "DeviceName": "Line",
  "Command": "SetUnitVars",
  "Units": {
    "u1": {
      "State": "",
      "Task": "",
      "Properties": {
        "command": "1111"
      }
    }
  }
}
```

**Важное примечание:

Ответ на `SetUnitVars` возвращает **только изменённые поля** (т.е. те, которые были переданы в запросе). Это **частичный ответ**, а не полный снимок состояния. Для получения полного состояния используйте `QueryAll`.

---

## Основные теги устройства Line

| Тег | Описание |
|-----|----------|
| `command` | Команда для выполнения (целое число в виде строки) |
| `message` | Результат обработки (копируется из command скриптом) |
| `Error` | Код ошибки ("0" = нет ошибки) |
| `ErrorMessage` | Текст ошибки |
| `cmdsuccess` | Признак успешного выполнения ("0" = нет, "1" = да) |
| `ST` | Статус |
| `Counter` | Счётчик операций юнита (число) |
| `State` | Состояние юнита |
| `Task` | Задача юнита |
| `batchId` | ID текущей партии |
| `CurItem` | Текущий элемент (формат: "код | кол-во | дата") |
| `batchIdCodesQueue` | Очередь ID партий |
| `setBatchID` | Установка ID партии |
| `devChangeBatch` | Изменение партии устройством |
| `devsChangeBatchIDQueueControl` | Контроль очереди смены партий |
| `devType` | Тип устройства (строка, например "10") |
| `LineID` | ID линии (строка, например "5") |
| `OnChangeBatchPrinters` | Используемая группа принтеров при смене партии |
| `Level1Printers` | Принтеры уровня 1 (через запятую) |
| `Level2Printers` | Принтеры уровня 2 |
| `OnChangeBatchCams` | Используемая группа камер при смене партии |
| `Level1Cams` | Камеры уровня 1 (через запятую) |
| `Level2Cams` | Камеры уровня 2 |
| `SignalCams` | Сигнальные камеры |
| `LineDevices` | Все устройства на линии (через запятую) |
| `enableErrors` | Включить обработку ошибок ("0" = нет, "1" = да) |

---

## Python-скрипт обработки

При изменении тега `command` сервер запускает `Line___Unit0_eval.py`:

```python
import traceback

a = unit1.changedprops  # словарь измененных тегов

if 'command' in a:
    cmd = int(unit1.getProperty('command'))
    if cmd != 0:
        try:
            debug('Your code')
            debug(str(cmd))
            unit1.setProperty('message', str(cmd))  # копируем в message
        except Exception as e: 
            unit1.setProperty('message', 'Error')
            debugsys('Except: ' + traceback.format_exc())
    unit1.setProperty('cmdsuccess', '0')
```

---

## Типичные ошибки

### ❌ Ошибка: "Could not convert string to integer"

**Причина:** `"Unit": "u1"` (строка) вместо `"Unit": 1` (число)

```json
// ❌ НЕПРАВИЛЬНО
{"DeviceName":"Line", "Unit":"u1", "Command":"SetUnitVars", ...}

// ✅ ПРАВИЛЬНО  
{"DeviceName":"Line", "Unit":1, "Command":"SetUnitVars", ...}
```

### ❌ Ошибка: Таймаут при чтении ответа

**Причина:** Использована несуществующая команда (GetUnitVars, QueryUnit и т.п.)

**Решение:** Используйте только `QueryAll` и `SetUnitVars`

### ❌ Ошибка: Кракозябры в ответе

**Причина:** Используется UTF-8 вместо windows-1251

```java
// ❌ НЕПРАВИЛЬНО
new String(response, StandardCharsets.UTF_8);

// ✅ ПРАВИЛЬНО
new String(response, Charset.forName("windows-1251"));
```

---

## Устройства в системе

| Устройство | Описание |
|------------|----------|
| `Line` | Основная линия производства |
| `Printer11` - `Printer14` | Принтеры уровня 1 |
| `CamChecker1`, `CamChecker2` | Камеры проверки |
| `CamAgregation1`, `CamAgregation2` | Камеры агрегации |
| `CamAgregationBox1`, `CamAgregationBox2` | Камеры агрегации коробок |
| `BatchQueue` | Очередь партий |

---

## Архитектура для мобильного приложения

```
┌─────────────────────┐
│  Мобильное прилож.  │
│   (Android/iOS)     │
└─────────┬───────────┘
          │ HTTP/REST
          ▼
┌─────────────────────┐
│   Spring Boot       │  ← Твой backend
│   REST API Server   │
└─────────┬───────────┘
          │ TCP Sockets (порт 10101)
          ▼
┌─────────────────────┐
│   MarkPrintServer   │  ← PrintSrv
│   (.NET Framework)  │
└─────────────────────┘
```

**Ваш Spring Boot сервер должен:**

1. Принимать REST-запросы от мобильного приложения
2. Преобразовывать их в TCP-команды для PrintSrv
3. Кешировать данные QueryAll (сервер отдаёт ВСЕ данные)

---

## Краткая шпаргалка

```
┌──────────────────────────────────────────────────────────────┐
│                     PRINTSERV CHEATSHEET                     │
├──────────────────────────────────────────────────────────────┤
│ Подключение:  127.0.0.1:10101, кодировка windows-1251        │
│ Фрейм:        [P001][4 байта длины BE][JSON]                 │
├──────────────────────────────────────────────────────────────┤
│ КОМАНДЫ (только 2!):                                         │
│                                                              │
│ QueryAll:     {"DeviceName":"Line","Command":"QueryAll"}     │
│               → возвращает JSON со всеми данными             │
│                                                              │
│ SetUnitVars:  {"DeviceName":"Line","Unit":1,                 │
│                "Command":"SetUnitVars",                      │
│                "Parameters":{"command":"555"}}               │
│               → возвращает ТОЛЬКО изменённые поля           │
│               → для полного состояния используйте QueryAll   │
├──────────────────────────────────────────────────────────────┤
│ ВАЖНО:                                                       │
│ • Unit — ЧИСЛО (1,2,3...), не строка "u1"!                   │
│ • Нумерация 1-based: Unit=1 → u1                             │
│ • Нет команды для чтения одного юнита — только QueryAll      │
└──────────────────────────────────────────────────────────────┘
```
