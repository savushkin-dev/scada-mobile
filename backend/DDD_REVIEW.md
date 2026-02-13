# DDD review: SCADA Mobile Backend

Дата анализа: 2026-02-13  
Проект: `scada-mobile/backend`

## 0) Контекст и оговорки

- Анализ выполнен по текущей структуре пакетов и ключевым классам (Spring Boot + in-memory store + polling scan cycle +
  TCP-интеграция с PrintSrv).
- Это *не классическая* предметная область с богатыми агрегатами/репозиториями и БД. Ваш домен ближе к **интеграционному
  bounded context** с жёсткими техническими ограничениями (только `QueryAll` и `SetUnitVars`, write-through cache,
  eventual consistency).
- Поэтому критерий «соответствует DDD» здесь стоит понимать как:
    - слои и зависимости направлены правильно;
    - доменная модель изолирована от инфраструктуры;
    - бизнес-инварианты кодируются/документируются доменными типами и политиками;
    - интеграция оформлена как ACL (anti-corruption layer).

## 1) Итоговая оценка (кратко)

**Сильные стороны (уже очень похоже на Clean Architecture / DDD-lite):**

- Есть явное разделение на слои: `api` → `services` (адаптер API) → `application` (use-cases) → `domain` (
  модель/политики), плюс `printsrv` (интеграция) и `store` (in-memory хранилище).
- **Чтение строго из snapshot** (write-through cache инвариант соблюдается), а запись из REST не ходит синхронно в
  PrintSrv — это хорошая архитектурная «граница консистентности».
- Есть **application ports** (`DeviceSnapshotReader/Writer`, `PendingWriteCommandsPort/DrainPort`) — это сильный признак
  портов/адаптеров.
- `PrintSrvMapper` выглядит как ACL: внешние DTO → доменные модели (`DeviceSnapshot`, `UnitSnapshot`, `UnitProperties`).

**Основные несоответствия «чистому DDD» (скорее DDD-smells, чем ошибки):**

- В домене есть зависимость от Spring: `domain/policy/LastWriteWinsPerUnitPolicy` помечен `@Component`.
- Термины протокола (например, `QueryAll`, `SetUnitVars`) заметно присутствуют в верхних слоях (логика названа командами
  интеграции). Это размывает **Ubiquitous Language** домена и смешивает bounded contexts.
- Доменная модель сейчас в основном **immutable data + инварианты** (records) — это нормально, но близко к **анемичной
  модели**, если доменные правила будут расти.
- Валидация входных параметров HTTP (`unit`, `value`) в основном «проезжает» слоями как `int` без явно выраженных value
  objects и без явного доменного диапазона.

Если оценивать «насколько соответствует DDD» по шкале:

- **Слоистая архитектура / ports & adapters:** 8/10
- **Чистота домена (без Spring/DTO/инфры):** 6/10 (из‑за `@Component` в policy)
- **Ubiquitous Language и bounded contexts:** 5/10
- **Богатство модели / инварианты в типах:** 6/10

В сумме: **проект ближе к Clean Architecture с элементами DDD (DDD-lite), чем к “полноценному DDD”** — и это может быть
оптимально для вашей предметной области.

## 2) Карта слоёв и фактические зависимости (as-is)

| Слой                        | Пакеты/классы (примеры)                                          | Роль                               | Зависит от                                       |
|-----------------------------|------------------------------------------------------------------|------------------------------------|--------------------------------------------------|
| Presentation (API)          | `api/controller/CommandsController`                              | HTTP endpoints, статусы, параметры | `services/*`, `exception/*`                      |
| API adapter                 | `services/CommandsService`, `api/ApiMapper`                      | адаптер: API ↔ application/domain  | `application/*`, `domain/*`                      |
| Application                 | `application/ScadaApplicationService`, `application/ports/*`     | use-cases и координация            | `domain/*`, `application/ports/*`                |
| Domain                      | `domain/model/*`, `domain/policy/*`                              | модель, инварианты, политики       | **(сейчас есть зависимость от Spring в policy)** |
| Infrastructure: store       | `store/PendingCommandsBuffer`, `store/PrintSrvSnapshotStore`     | in-memory snapshot + buffer        | `domain/*`, `application/ports/*`                |
| Infrastructure: polling     | `services/polling/*`                                             | scan cycle, retry/reconnect        | `application/ports/*`, `domain/*`, `printsrv/*`  |
| Infrastructure: integration | `printsrv/client/*`, `printsrv/dto/*`, `printsrv/PrintSrvMapper` | TCP, framing, DTO, mapping         | `domain/*` (через mapper)                        |

**Плюс:** `exception/GlobalExceptionHandler` — инфраструктурный слой для API.

### Наблюдение

Слои в целом направлены правильно, но есть пара “протечек” терминов и зависимостей (см. раздел 4).

## 3) Что хорошо с точки зрения DDD/архитектуры

### 3.1. Порты и адаптеры действительно присутствуют

- `application/ports/*` создают правильную зависимость: application знает *интерфейсы*, а реализация лежит в
  infrastructure (`store/*`).
- Polling (`PrintSrvPollingScheduler`) тоже работает через порты: пишет snapshot через `DeviceSnapshotWriter`, забирает
  pending через `PendingWriteCommandsDrainPort`.

Это очень хороший фундамент для DDD/Clean Architecture.

### 3.2. Anti-Corruption Layer вокруг PrintSrv

- `PrintSrvMapper` преобразует `QueryAllResponseDTO` → `DeviceSnapshot`.
- Таким образом доменные типы не тащат за собой DTO слои наружу.

### 3.3. Инварианты write-through cache явно выражены

Судя по `CommandsController` + `ScadaApplicationService` + `PrintSrvPollingScheduler`:

- GET читает snapshot из store.
- POST кладёт команду в буфер.
- Единственный поток scan cycle выполняет READ → LOGIC → WRITE → UPDATE.

Для такого типа системы это важнее, чем «богатая модель агрегатов».

## 4) Найденные проблемы/DDD-smells (с объяснениями)

### 4.1. Домен зависит от Spring (`@Component` в `domain.policy`)

Факт: `domain/policy/LastWriteWinsPerUnitPolicy` импортирует `org.springframework.stereotype.Component`.

Почему это проблема в DDD:

- Доменный слой становится не “pure Java”: его нельзя переиспользовать/тестировать без инфраструктурного контекста.
- Инфраструктура протекает внутрь модели.

Риск:

- Со временем в домен могут «протечь» и другие аннотации (`@Value`, `@Transactional`, Jackson), и домен превратится в
  «слой сущностей, завязанный на Spring».

### 4.2. Ubiquitous Language смешан с интеграционными командами

Примеры (по комментариям/логам и названиям): `QueryAll`, `SetUnitVars`, `PrintSrv` встречаются в верхних слоях и даже в
занчениях полей (`"SetUnitVars"`, `"QueryAll"`, `"Line"`).

Почему это важно:

- В DDD **UL должен отражать предметную область**, а не протокол внешнего сервиса.
- `QueryAll`/`SetUnitVars` — это термины интеграционного контекста (PrintSrv), их лучше держать внутри bounded context
  интеграции.

Риск:

- Когда появится другой источник данных/другой протокол, вы будете вынуждены «переименовывать домен», хотя доменная
  семантика (снимок состояния, команда записи) останется той же.

### 4.3. Анемичность доменной модели (потенциальная)

Сейчас домен (`DeviceSnapshot`, `UnitSnapshot`, `WriteCommand`) — immutable records + конструкторные инварианты. Это
хороший старт.

Но DDD ожидает, что **доменные правила живут рядом с моделью**, а не растворяются в application/infrastructure.

Здесь риск в будущем:

- Если появятся правила типа «нельзя писать значение X в состоянии Y», «проверка диапазонов по типу устройства»,
  «политика согласования команд» — и всё это окажется в `services/*` или `application/*`, домен останется “data-only”.

### 4.4. Валидация и доменные ограничения выражены слабо на границе

В API сейчас принимаются `@RequestParam int unit, int value` (в `CommandsController`).

Почему это DDD-smell:

- Доменные ограничения («unit 1-based», допустимые диапазоны `value`) лучше выражать через **value objects** или единый
  доменный валидатор, чтобы это не стало “копипастой” по слоям.

Риск:

- Разные части системы начнут по-разному понимать «валидный unit/value».

### 4.5. Пакет `services` как “mixed bag”

Сейчас `services` содержит как API-адаптер (`CommandsService`, `HealthService`), так и polling (`services/polling/*`).
Это *рабоче*, но термин `services` в DDD обычно двусмысленный.

Риск:

- Пакет “services” станет местом, куда складывается всё подряд (application service, domain services, infrastructure
  services).

## 5) Рекомендации по улучшению (без изменения протокола и инвариантов)

Ниже — предложения, которые улучшат соответствие DDD/Clean Architecture, не ломая текущие архитектурные инварианты (
snapshot-only GET, быстрый POST, scan cycle).

### Quick wins (низкий риск, высокий эффект)

1) **Убрать Spring-аннотации из домена**
    - Что сделать: `domain.policy.*` оставить как чистые Java-классы/интерфейсы без `@Component`.
    - Где вместо этого собирать бин: в infrastructural конфиге (например `config/*`) или в пакете `services`/
      `infrastructure`.
    - Почему: домен станет «чистым» и легко тестируемым.

2) **Явно зафиксировать bounded contexts в документации и правила импорта**
    - Предложение: добавить (в docs/README или отдельном документе) простые правила:
        - `domain` не импортирует `org.springframework.*`, `printsrv.*`, `api.*`.
        - `api` не импортирует `printsrv.*`.
        - `application` зависит от `domain` и `application.ports`, но не от `printsrv.client`.

3) **Переименовать/пересобрать “термины” на уровне мышления (даже без переименований в коде)**
    - Договориться, что доменные понятия: `Snapshot`, `WriteCommand`, `ScanCycle`.
    - А `QueryAll`/`SetUnitVars` — исключительно уровнем integration.

### Medium (умеренный риск/работа)

4) **Ввести проектное правило: value objects для критичных параметров**
    - Например, в дизайне: `UnitNumber`, `CommandValue`.
    - Плюс: единая валидация, меньше «магических int».
    - Минус: потребует рефакторинга сигнатур по слоям.

5) **Уточнить роль application service как use-case слой**
    - Сейчас `ScadaApplicationService` выглядит корректно, но можно усилить DDD-подачу:
        - методы = use-cases (что уже близко: `getCurrentState`, `submitWriteCommand`),
        - чётко описать их контракты как “application boundary” (особенно eventual consistency).

6) **Развести пакет `services` на более говорящие части**
    - Например: `adapters.http/*`, `infrastructure.polling/*`, `infrastructure.health/*`.
    - Это не обязательно, но улучшает навигацию и дисциплину зависимостей.

### Long-term (архитектурные улучшения)

7) **Выделить поддомены/агрегаты, если бизнес-правил станет больше**
    - Если появятся правила по устройствам/линиям/партиям (batch), then:
        - `Device` как агрегат,
        - `Unit` как entity внутри агрегата,
        - доменные сервисы для сложных политик.

8) **Тестовая стратегия “DDD-friendly”**
    - Unit-тесты для:
        - доменных value objects/инвариантов,
        - `LastWriteWinsPolicy`,
        - `PrintSrvMapper` как ACL.
    - Integration-тесты для scan cycle (с заглушкой PrintSrv).

## 6) Конкретные «точки» в коде, которые я бы использовал как ориентиры при рефакторинге

- Чистка домена от Spring:
    - `domain/policy/LastWriteWinsPerUnitPolicy` (убрать `@Component`, конфигурировать снаружи).
- Разграничение UL и integration:
    - Строковые значения `"QueryAll"`, `"SetUnitVars"`, `"Line"` сейчас находятся в
      `services/polling/ScadaCommandExecutor` и `api/ApiMapper`.
- Укрепление доменных инвариантов:
    - входные параметры `unit/value` в `api/controller/CommandsController` → потенциально value objects.

## 7) Coverage требований и вывод

- Глубокий анализ соответствия DDD: **сделано** (структура слоёв, доменная модель, порты/адаптеры, ACL, bounded
  contexts/UL, smells).
- Изменения в проект: **не вносились** (кроме добавления этого отчёта).
- Результат оформлен в корне проекта: **`DDD_REVIEW.md`**.

