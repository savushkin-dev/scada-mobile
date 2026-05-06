# 🧪 Mock-данные для эмуляции PrintSrv (dev-профиль)

> **Применимость:** Только профиль `dev` (`--spring.profiles.active=dev`).  
> В профиле `prod` все mock-компоненты не создаются — работает реальный TCP-клиент PrintSrv.


## Purpose
Описание mock-слоя PrintSrv для dev-профиля, его конфигурации и поведения.

## Table of contents

1. [Зачем нужны mock-данные](#1-зачем-нужны-mock-данные)
2. [Архитектура mock-слоя](#2-архитектура-mock-слоя)
3. [Компоненты и их роли](#3-компоненты-и-их-роли)
4. [Seed-файлы XML: формат и структура](#4-seed-файлы-xml-формат-и-структура)
5. [Устройства и их ключевые свойства](#5-устройства-и-их-ключевые-свойства)
6. [Симуляция изменений состояния (MockStateSimulator)](#6-симуляция-изменений-состояния-mockstatesimulator)
7. [Конфигурация mock-слоя (application-dev.yaml)](#7-конфигурация-mock-слоя-application-devyaml)
8. [Offline-режим: эмуляция недоступности](#8-offline-режим-эмуляция-недоступности)
9. [Пользовательские seed-файлы (filesystem override)](#9-пользовательские-seed-файлы-filesystem-override)
10. [Потокобезопасность](#10-потокобезопасность)
11. [Диагностика и логи](#11-диагностика-и-логи)
12. [Быстрые рецепты](#12-быстрые-рецепты)

---

## 1. Зачем нужны mock-данные

Backend работает как прослойка между мобильным клиентом и сервисом PrintSrv по TCP.
В dev-окружении реальный PrintSrv недоступен (или неудобен для локальной разработки),
поэтому предусмотрен полноценный mock-слой:

- Реализует тот же интерфейс `PrintSrvClient`, что и prod-клиент.
- Загружает начальное состояние из XML-файлов (реальные «снимки» с PrintSrv).
- Симулирует изменение состояния аппаратов в фоне через `@Scheduled` тик.
- Поддерживает offline-режим для тестирования retry/recovery-логики.

---

## 2. Архитектура mock-слоя

```
┌─────────────────────────────────────────────────────────────────────┐
│  @Profile("dev")  — активируется только при spring.profiles.active=dev  │
└─────────────────────────────────────────────────────────────────────┘

application-dev.yaml
  └─ printsrv.mock.*
       │
       ▼
MockPrintSrvConfig (@Configuration)
  └── @EnableConfigurationProperties(MockPrintSrvProperties)
           │
           ▼
MockPrintSrvClientRegistry (@Component)
  │   ┌─────────────────────────────────────┐
  │   │ На старте (@PostConstruct):          │
  │   │  1. printsrv.instances → список ID   │
  │   │  2. XmlSnapshotLoader → seed-данные  │
  │   │  3. Создаёт MockPrintSrvClient ×N    │
  │   └─────────────────────────────────────┘
  │
  ├─ MockPrintSrvClient("trepko1", state, offline=false)
  ├─ MockPrintSrvClient("trepko2", state, offline=false)
  ├─ ...
  └─ MockPrintSrvClient("bosch",   state, offline=true)   ← пример offline
           │
           │  implements PrintSrvClient
           ▼
  ScanCycleScheduler (prod+dev)
    → client.queryAll(deviceName)
    → PrintSrvSnapshotStore.save(...)

MockStateSimulator (@Component, @Scheduled)
  └─ каждые tick-interval-ms → tickAll()
       └─ изменяет MockInstanceState через write-lock
```

**Ключевой инвариант:** `ScanCycleScheduler` не знает, с каким клиентом работает —
реальным TCP или мок. Весь интерфейс заменяется через `PrintSrvClientRegistry`.

---

## 3. Компоненты и их роли

| Класс                        | Роль                                                                                             |
|------------------------------|--------------------------------------------------------------------------------------------------|
| `MockPrintSrvConfig`         | Включает `MockPrintSrvProperties` как типизированный бин, ограничивает scope профилем `dev`      |
| `MockPrintSrvProperties`     | Типизированный `@ConfigurationProperties(prefix="printsrv.mock")` — параметры симуляции          |
| `MockPrintSrvClientRegistry` | Фабрика mock-клиентов; читает `printsrv.instances` и создаёт по клиенту на каждый аппарат        |
| `MockPrintSrvClient`         | Реализует `PrintSrvClient`; при `queryAll()` строит `QueryAllResponseDTO` из `MockInstanceState` |
| `MockInstanceState`          | Потокобезопасное in-memory состояние одного аппарата (`deviceName → {key → value}`)              |
| `XmlSnapshotLoader`          | Загружает seed-состояние из XML-файлов (filesystem или classpath)                                |
| `MockStateSimulator`         | `@Scheduled` тик: изменяет счётчики, флаги ошибок, временные метки в `MockInstanceState`         |

---

## 4. Seed-файлы XML: формат и структура

### 4.1 Формат файла

Файлы имеют формат .NET DataContract XML (реальный формат экспорта PrintSrv):

```xml
<DeviceUnit
  xmlns:i="http://www.w3.org/2001/XMLSchema-instance"
  xmlns="http://schemas.datacontract.org/2004/07/MarkPrintServer.Classes">
  <counterloginterval>1000</counterloginterval>
  <properties xmlns:d2p1="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
    <d2p1:KeyValueOfstringstring>
      <d2p1:Key>ST</d2p1:Key>
      <d2p1:Value>1</d2p1:Value>
    </d2p1:KeyValueOfstringstring>
    <d2p1:KeyValueOfstringstring>
      <d2p1:Key>CurItem</d2p1:Key>
      <d2p1:Value>1605 | 147 | 19.08.2025</d2p1:Value>
    </d2p1:KeyValueOfstringstring>
    ...
  </properties>
</DeviceUnit>
```

Каждый элемент `<d2p1:KeyValueOfstringstring>` — одна пара `ключ → значение`.
`XmlSnapshotLoader` парсит XML и возвращает `Map<String, String>`.

### 4.2 Именование файлов

```
{DeviceName}___Unit0.xml
```

Три подчёркивания — формат самого PrintSrv. Примеры:

- `Line___Unit0.xml`
- `Printer11___Unit0.xml`
- `CamAgregation___Unit0.xml`

### 4.3 Стратегия загрузки (приоритеты)

```
1. Filesystem:  {snapshotBaseDir}/{instanceId}/{Device}___Unit0.xml
        ↓ (если файл не найден или пуст)
2. Classpath:   mock-snapshots/default/{Device}___Unit0.xml
        ↓ (если и classpath-файл отсутствует)
3. Пустой map + WARNING в логе (аппарат стартует с null-значениями)
```

**Classpath-дефолты** (`src/main/resources/mock-snapshots/default/`):

| Файл                           | Описание                                                    |
|--------------------------------|-------------------------------------------------------------|
| `Line___Unit0.xml`             | Линия маркировки: ST=0, CurItem=`1605 \| 147 \| 19.08.2025` |
| `BatchQueue___Unit0.xml`       | Очередь заданий: Item01…Item20 = «Пусто»                    |
| `Printer11___Unit0.xml`        | Принтер: продукт «Сырок гл. МАКОВКА», партия 484            |
| `CamAgregation___Unit0.xml`    | Камера агрегации: ST=1, Total/Succeeded/Failed              |
| `CamAgregationBox___Unit0.xml` | Камера агрегации короба: ST=1                               |
| `CamChecker___Unit0.xml`       | Камера проверки: ST=1, Total/Succeeded/Failed               |
| `scada___Unit0.xml`            | SCADA-метаданные: Dev041/Dev042 счётчики, lineerr           |

### 4.4 Особенности парсинга

`XmlSnapshotLoader` умеет обрабатывать реальные PrintSrv-файлы, которые могут содержать:

- **XML character references** на управляющие символы (`&#x1D;` — GS1-сепаратор в `LastRead`).
  Они стрипаются через `sanitizeXmlCharRefs()` перед парсингом.
- **BOM (Byte Order Mark)** в начале файла — обрабатывается прозрачно.
- **Пустые значения** (`<d2p1:Value />`): сохраняются как `""`.

---

## 5. Устройства и их ключевые свойства

Список устройств фиксирован в `XmlSnapshotLoader.KNOWN_DEVICES` и `ScanCycleScheduler.DEVICES`:

### Line — основная линия маркировки

| Ключ             | Описание                                          | Пример                      |
|------------------|---------------------------------------------------|-----------------------------|
| `ST`             | Статус линии: `0` = остановлена, `1` = работает   | `0`                         |
| `CurItem`        | Текущий счётчик: `"{count} \| {batch} \| {date}"` | `1605 \| 147 \| 19.08.2025` |
| `Error`          | Флаг ошибки: `0` / `1`                            | `0`                         |
| `ErrorMessage`   | Текст ошибки (пусто при отсутствии)               | `""`                        |
| `LastReadTime`   | Время последнего чтения (HH:mm:ss)                | `14:32:10`                  |
| `Level1Printers` | Привязанный принтер                               | `Printer11`                 |

> **Симулируется:** При `ST=1` счётчик в `CurItem` растёт на +1 каждый тик.
> Флаг `Error` случайно инвертируется с вероятностью `error-flip-probability` независимо от `ST`.

---

### BatchQueue — очередь заданий печати

| Ключ                     | Описание                | Пример                           |
|--------------------------|-------------------------|----------------------------------|
| `CurItem`                | Индекс текущего задания | `1`                              |
| `Item01`…`Item20`        | Задания в очереди       | `"Пусто"` / `"Сырок МАКОВКА..."` |
| `Error` / `ErrorMessage` | Флаг и текст ошибки     | `0` / `""`                       |

> **Симулируется:** не изменяется автоматически; данные статичны из seed-файла.

---

### Printer11 — принтер маркировки

| Ключ                              | Описание               | Пример                        |
|-----------------------------------|------------------------|-------------------------------|
| `ST`                              | Статус принтера        | `1`                           |
| `CurItem`                         | Счётчик отпечатанных   | `1605 \| 147 \| 19.08.2025`   |
| `descr`                           | Название продукта      | `"Сырок гл. МАКОВКА 20% 40г"` |
| `ean13`                           | EAN-13 штрихкод        | `4810268055485`               |
| `partynumber`                     | Номер партии           | `484`                         |
| `dateproduced` / `dateexpiration` | Даты выпуска/истечения | `07.06.2024`                  |
| `printpath`                       | Путь к шаблону печати  | `Sliser_3`                    |
| `ip`                              | IP-адрес принтера      | `999.9.9.9`                   |

> **Симулируется:** При `ST=1` счётчик в `CurItem` растёт на +1 каждый тик.

---

### CamAgregation / CamAgregationBox — камеры агрегации

| Ключ                         | Описание                          | Пример                    |
|------------------------------|-----------------------------------|---------------------------|
| `ST`                         | Статус камеры                     | `1`                       |
| `Total`                      | Всего считано пачек               | `1500`                    |
| `Succeeded`                  | Успешно считано                   | `1498`                    |
| `Failed`                     | Забракованных                     | `2`                       |
| `BatchTotal` / `BatchFailed` | Счётчики по партии                | `500` / `0`               |
| `LastRead`                   | Последний прочитанный Data Matrix | `<start>01048102...`      |
| `LastReadTime`               | Время последнего чтения           | `2026-02-23 04:24:40.640` |
| `kd` / `kdBox`               | Коды упаковки                     | `18` / `17`               |
| `les`                        | Номер линии (код)                 | `0202020601`              |

> **Симулируется:** При `ST=1` за каждый тик добавляется от 1 до 5 пачек в `Total`/`Succeeded`.
> С вероятностью `error-flip-probability` появляется брак: `Failed` и `BatchFailed` увеличиваются.

---

### CamChecker — камера проверки

| Ключ                             | Описание                      |
|----------------------------------|-------------------------------|
| `ST`                             | Статус                        |
| `Total` / `Failed` / `Succeeded` | Счётчики проверки             |
| `LastRead` / `LastReadTime`      | Последние данные сканирования |
| `errWorkCounter`                 | Счётчик ошибок работы         |

> **Симулируется:** не изменяется автоматически (нет tickCamChecker); данные статичны.

---

### scada — метаданные SCADA-системы

| Ключ                                      | Описание                   |
|-------------------------------------------|----------------------------|
| `ST`                                      | Статус                     |
| `lineerr`                                 | Флаг ошибки линии          |
| `ean` / `batch` / `batchid`               | Текущий EAN и партия       |
| `Dev041CounterGeneral` / `Dev041Fail` / … | Счётчики по устройству 041 |
| `Dev042CounterGeneral` / `Dev042Fail` / … | Счётчики по устройству 042 |

> **Симулируется:** не изменяется автоматически; данные статичны из seed-файла.

---

## 6. Симуляция изменений состояния (MockStateSimulator)

`MockStateSimulator` — Spring `@Component` (`@Profile("dev")`), имеет `@Scheduled`-метод.

### 6.1 Цикл симуляции

```
@Scheduled(fixedDelay = ${printsrv.mock.tick-interval-ms})
tick()
  └─ if (!simulationEnabled) return
  └─ tickAll()
       └─ for each non-offline MockPrintSrvClient:
            tickInstance(client)
              ├─ tickCamAggregation(state, "CamAgregation")
              ├─ tickCamAggregation(state, "CamAgregationBox")
              ├─ tickLine(state)
              └─ tickPrinter(state, "Printer11")
```

`fixedDelay` означает: задержка отсчитывается **после завершения** предыдущего тика.
При долгих итерациях задачи не накапливаются в очередь.

### 6.2 Логика по устройствам

#### tickLine

```
if random() < error-flip-probability:
    Error = 1 - Error   ← инвертировать независимо от ST

if ST == "1":
    CurItem = incrementCurItemCounter(CurItem)   ← +1 к первому числу
    LastReadTime = текущее время (HH:mm:ss)
```

#### tickCamAggregation (применяется к CamAgregation и CamAgregationBox)

```
if ST != "1": skip

increment = random(1..5)   ← пачки за тик
Total     += increment
Succeeded += increment

if random() < error-flip-probability:
    failed = random(1..2)
    Failed     += failed
    BatchFailed += 1
```

#### tickPrinter (Printer11)

```
if ST != "1": skip
CurItem = incrementCurItemCounter(CurItem)   ← +1 к первому числу
```

### 6.3 Формат CurItem и его инкремент

`CurItem` для Line и Printer11 содержит строку вида:

```
"1605 | 147 | 19.08.2025"
 ^count  ^batch  ^date
```

Функция `incrementCurItemCounter` парсит первый токен до `|`, инкрементирует его и собирает строку обратно:

```
"1605 | 147 | 19.08.2025"  →  "1606 | 147 | 19.08.2025"
"42"                        →  "43"
"abc | 10"                  →  "abc | 10"   (нераспознанный формат — без изменений)
```

### 6.4 Детерминированность

`MockStateSimulator` использует `java.util.Random` с seed из конфигурации (`random-seed: 42`).
При одинаковом seed последовательность `tickAll()` воспроизводима — это позволяет unit-тестам
точно предсказывать состояние через N тиков без «магических» чисел.

---

## 7. Конфигурация mock-слоя (application-dev.yaml)

```yaml
printsrv:
  mock:
    # Путь к кастомным seed-файлам. Если пусто — только classpath-дефолты.
    # Структура: {snapshotBaseDir}/{instanceId}/{Device}___Unit0.xml
    # Пример: snapshot-base-dir: "C:/PrintSrv/snapshots"
    snapshot-base-dir:

    # Интервал симуляционного тика (мс).
    # По умолчанию 2000 мс (2 сек). Увеличьте для медленной отладки.
    tick-interval-ms: 2000

    # Инстансы, стартующие в offline-режиме (все вызовы queryAll → IOException).
    # Пример: offline-instances: [bosch, grunwald11]
    offline-instances: []

    # Вероятность инверсии флага ошибки за один тик (0.0 — никогда, 1.0 — всегда).
    error-flip-probability: 0.03

    # Seed для Random. Изменение seed меняет порядок событий в тестах.
    random-seed: 42

    # Выключить автоматические тики без пересборки.
    simulation-enabled: true
```

### Параметры и их эффект

| Параметр                 | Умолчание | Что меняет                                          |
|--------------------------|-----------|-----------------------------------------------------|
| `snapshot-base-dir`      | `null`    | Путь к кастомным XML; при `null` — только classpath |
| `tick-interval-ms`       | `2000`    | Частота изменений состояния в dev-сессии            |
| `offline-instances`      | `[]`      | Какие аппараты недоступны (тест retry/recovery)     |
| `error-flip-probability` | `0.03`    | Частота ошибок (3% = ≈1 ошибка раз в 33 тика)       |
| `random-seed`            | `42`      | Воспроизводимость для тестов                        |
| `simulation-enabled`     | `true`    | Быстрый off-switch без пересборки                   |

---

## 8. Offline-режим: эмуляция недоступности

Инстансы из `offline-instances` при вызове `queryAll()` сразу выбрасывают `IOException`:

```
java.io.IOException: [trepko1] MockPrintSrvClient is offline (simulated TCP failure)
```

Это позволяет тестировать, как `ScanCycleScheduler` обрабатывает недоступность аппарата:

- Snapshot для этого аппарата не обновляется.
- Scheduler переходит к следующему аппарату (`continue`).
- Последний валидный snapshot остаётся доступным клиентам (graceful degradation).

`isAlive()` для offline-инстанса возвращает `false`.
`MockStateSimulator.tickAll()` пропускает offline-инстансы — их состояние не меняется.

**Пример конфигурации:**

```yaml
printsrv:
  mock:
    offline-instances: [bosch, grunwald11]
```

---

## 9. Пользовательские seed-файлы (filesystem override)

Чтобы запустить аппарат с реальным состоянием вместо classpath-дефолтов:

### 9.1 Структура директорий

```
{snapshotBaseDir}/
  trepko1/
    Line___Unit0.xml
    BatchQueue___Unit0.xml
    Printer11___Unit0.xml
    CamAgregation___Unit0.xml
    CamAgregationBox___Unit0.xml
    CamChecker___Unit0.xml
    scada___Unit0.xml
  trepko2/
    Line___Unit0.xml
    ...
```

### 9.2 Частичное переопределение

Можно положить только часть файлов — остальные `XmlSnapshotLoader` возьмёт из classpath:

```
mySnapshots/
  trepko1/
    Line___Unit0.xml          ← filesystem (реальный снимок)
    CamAgregation___Unit0.xml ← filesystem (реальный снимок)
    # BatchQueue, Printer11, ... — из classpath/mock-snapshots/default/
```

### 9.3 Включение

```yaml
printsrv:
  mock:
    snapshot-base-dir: "C:/PrintSrv/snapshots"
    # или на Linux/Mac:
    # snapshot-base-dir: "/home/user/scada/snapshots"
```

---

## 10. Потокобезопасность

### MockInstanceState

Каждый инстанс имеет свой `MockInstanceState` с `ReentrantReadWriteLock`:

| Операция                           | Lock                                                             |
|------------------------------------|------------------------------------------------------------------|
| `getPropertiesCopy(device)`        | **Read-lock** — множественное параллельное чтение без блокировки |
| `setProperty(device, key, val)`    | **Write-lock** — исключительный доступ                           |
| `incrementInt(device, key, delta)` | **Write-lock** — атомарный read-modify-write                     |
| `initDevice(device, props)`        | **Write-lock** — только при старте                               |

### Потоки в dev-профиле

```
Spring Scheduler (1 поток)
  └─ MockStateSimulator.tick()       ← write через MockInstanceState

Spring Scheduler (1 поток)
  └─ ScanCycleScheduler.scanCycle()  ← read через MockPrintSrvClient.queryAll()
                                          └─ MockInstanceState.getPropertiesCopy()

HTTP/WebSocket threads (N потоков)
  └─ читают snapshot из PrintSrvSnapshotStore  ← не обращаются к MockInstanceState
```

Гонок нет: симулятор пишет через write-lock, polling читает через read-lock.

---

## 11. Диагностика и логи

### Ключевые лог-сообщения при старте

```
INFO  MockPrintSrv: registered instance 'trepko1' (displayName='Trepko №1', offline=false)
INFO  MockPrintSrv: registered instance 'bosch'   (displayName='Bosch',     offline=true)
INFO  MockPrintSrvClientRegistry: 14 client(s) ready (1 offline)
```

### Загрузка seed-файлов

```
DEBUG [trepko1] Loading Line from classpath: mock-snapshots/default/Line___Unit0.xml
WARN  [trepko1] No seed file found for device scada. Instance will start with empty state.
```

### Тики симулятора (уровень TRACE/DEBUG)

```
DEBUG [trepko1] Line — Error flipped to 1
TRACE [trepko1] CamAgregation — 3 failed codes this tick
```

### Включить детальные логи через Actuator (без перезапуска)

```http
POST http://localhost:8080/actuator/loggers/dev.savushkin.scada.mobile.backend
Content-Type: application/json

{"configuredLevel": "TRACE"}
```

Или только для mock-пакета:

```http
POST http://localhost:8080/actuator/loggers/dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock
Content-Type: application/json

{"configuredLevel": "TRACE"}
```

---

## 12. Быстрые рецепты

### Запустить в dev-профиле

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

### Проверить готовность после старта

```powershell
Invoke-RestMethod http://localhost:8080/api/v1.0.0/health/ready
# Ожидание: { "ready": true, "status": "UP" }
```

### Увидеть данные первого аппарата

```powershell
Invoke-RestMethod http://localhost:8080/api/v1.0.0/workshops/dess/units
```

### Остановить симуляцию без пересборки

```yaml
# application-dev.yaml
printsrv:
  mock:
    simulation-enabled: false
```

### Ускорить симуляцию для демонстрации

```yaml
printsrv:
  mock:
    tick-interval-ms: 500       # тик каждые 0.5 сек
    error-flip-probability: 0.2 # ошибки каждые ~5 тиков
```

### Запустить все аппараты в offline (только retry-тест)

```yaml
printsrv:
  mock:
    offline-instances:
      - trepko1
      - trepko2
      - hassia1
      - hassia2
      - hassia4
      - hassia5
      - hassia6
      - grunwald1
      - grunwald2
      - hassia3
      - bosch
      - grunwald5
      - grunwald8
      - grunwald11
```

### Детерминированный seed для тестов

```yaml
printsrv:
  mock:
    random-seed: 42       # предсказуемая последовательность
    simulation-enabled: false  # тики управляются вручную через tickAll()
```

---

**Дата:** 2026-03-02  
**Профиль:** `dev`  
**Статус:** актуально для текущей версии mock-слоя
