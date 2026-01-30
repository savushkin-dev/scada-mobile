# Документация API PrintSrv

> **Документация составлена на основе реверс-инжиниринга сервера MarkPrintServer.exe и тестирования протокола.**  
> Последнее обновление: 30 января 2026 г.

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

**Ответ:**

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
        "command": "555",
        "message": "555",
        "Error": "0",
        "ErrorMessage": "",
        "CurItem": "1605 | 147 | 19.08.2025",
        "batchId": "",
        "cmdsuccess": "0",
        "ST": "0"
      }
    }
  }
}
```

**Особенности:**

- Возвращает ВСЕ юниты со ВСЕМИ тегами
- Нет возможности запросить только один юнит (такой команды нет)
- Используйте для: мониторинга, проверки результата после SetUnitVars

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

**Ответ:**

```
Fail
```

⚠️ **ОСОБЕННОСТЬ СЕРВЕРА:** Команда SetUnitVars **всегда возвращает `"Fail"`**, даже при успешном выполнении! Это баг/особенность сервера.

**Как проверить, что команда выполнилась:**

1. Отправьте SetUnitVars
2. Игнорируйте ответ "Fail"
3. Сделайте QueryAll
4. Проверьте, изменилось ли значение тега

**Пример установки нескольких тегов:**

```json
{
  "DeviceName": "Line",
  "Unit": 1,
  "Command": "SetUnitVars",
  "Parameters": {
    "command": "777",
    "batchId": "12345"
  }
}
```

---

## Основные теги устройства Line

| Тег | Описание |
|-----|----------|
| `command` | Команда для выполнения (целое число) |
| `message` | Результат обработки (копируется из command скриптом) |
| `Error` | Код ошибки ("0" = нет ошибки) |
| `ErrorMessage` | Текст ошибки |
| `cmdsuccess` | Признак успешного выполнения |
| `CurItem` | Текущий элемент |
| `batchId` | ID партии |
| `ST` | Статус |

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

## Пример Java-клиента

```java
public class PrintServerClient {
    private static final byte[] MAGIC = {'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");
    
    private final String host;
    private final int port;
    
    public PrintServerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Отправляет команду и получает ответ
     */
    public String sendCommand(String json) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Отправка
            byte[] data = json.getBytes(CHARSET);
            out.write(MAGIC);
            out.writeInt(data.length);  // Big Endian по умолчанию в Java
            out.write(data);
            out.flush();
            
            // Получение ответа
            byte[] magic = new byte[4];
            in.readFully(magic);
            int length = in.readInt();
            byte[] response = new byte[length];
            in.readFully(response);
            
            return new String(response, CHARSET);
        }
    }
    
    /**
     * Получить все теги устройства
     */
    public String queryAll(String deviceName) throws IOException {
        String json = String.format(
            "{\"DeviceName\":\"%s\",\"Command\":\"QueryAll\"}", 
            deviceName
        );
        return sendCommand(json);
    }
    
    /**
     * Установить значение тега
     * @param unitNumber номер юнита (1-based: 1=u1, 2=u2, ...)
     * 
     * ВАЖНО: Возвращает "Fail" даже при успехе! Проверяйте через queryAll.
     */
    public String setUnitVar(String deviceName, int unitNumber, 
                             String tagName, String value) throws IOException {
        String json = String.format(
            "{\"DeviceName\":\"%s\",\"Unit\":%d,\"Command\":\"SetUnitVars\"," +
            "\"Parameters\":{\"%s\":\"%s\"}}", 
            deviceName, unitNumber, tagName, value
        );
        return sendCommand(json);
    }
}
```

### Использование

```java
PrintServerClient client = new PrintServerClient("127.0.0.1", 10101);

// 1. Читаем текущие значения
String before = client.queryAll("Line");
System.out.println("До: " + extractCommandValue(before));

// 2. Устанавливаем новое значение (Unit=1 для u1!)
String result = client.setUnitVar("Line", 1, "command", "999");
// result будет "Fail" - это нормально!

// 3. Проверяем через QueryAll
String after = client.queryAll("Line");
System.out.println("После: " + extractCommandValue(after));
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

### ❌ SetUnitVars возвращает "Fail"

**Это НЕ ошибка!** Сервер всегда возвращает "Fail" для SetUnitVars.  
Проверяйте результат через QueryAll.

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
4. Обрабатывать особенность с "Fail" ответом

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
│               → возвращает "Fail" (это нормально!)           │
│               → проверяйте результат через QueryAll          │
├──────────────────────────────────────────────────────────────┤
│ ВАЖНО:                                                       │
│ • Unit — ЧИСЛО (1,2,3...), не строка "u1"!                   │
│ • Нумерация 1-based: Unit=1 → u1                             │
│ • SetUnitVars всегда "Fail" — это баг сервера                │
│ • Нет команды для чтения одного юнита — только QueryAll      │
└──────────────────────────────────────────────────────────────┘
```
