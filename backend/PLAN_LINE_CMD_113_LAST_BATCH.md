# План: детектирование «последней партии» по `Line.CMD = 113` (PrintSrv) и сигнал на фронт

> План предназначен для **двух независимых агентов**: один делает реализацию,
> второй — тестирование. Общий контракт фиксируется здесь; обе стороны
> должны следовать этому документу.

## 1. Контекст и цель

Работник на автомате нажимает кнопку → в поле `CMD` устройства `Line`
(PrintSrv) выставляется код **`113`**. Пока `113` присутствует в поле —
уведомление «последняя партия» **должно быть активно**; когда `113` исчез
(работник снял / партия закрыта) — уведомление **снимается**. Это
**уровневый** сигнал, а не импульс.

Бэкенд опрашивает PrintSrv (как и сейчас), следит за значением `CMD` и
включает/выключает уже существующий механизм «последняя партия», который
мгновенно рассылает `NOTIFICATION` на фронт через WebSocket.

Идентификация источника: устройства/автоматы идентифицируются по **ip + port**.
В коде это уже абстрагировано: каждый PrintSrv инстанс имеет `instanceId`
(например, `hassia2`), который в БД топологии сопоставлен с host:port аппарата.
Работаем в терминах `instanceId` — это и есть идентичность «ip+port».

**Что сознательно НЕ делаем в этой задаче:** полноценный workflow для
автоматов (accept/complete/отмена, «кто принял») — это потребует общения
SCADA с нашим беком, а пока общение идёт только через PrintSrv. Автомат
только включает/выключает сигнал; вся обработка уведомления (принял/выполнил)
остаётся за работниками через существующий API. Это отдельная будущая задача.

Аутентификация/авторизация автоматов (machine-JWT) **не используется**:
сигнал рождается внутри бэкенда из polling-потока, никакого REST-вызова и
проверки токена не требуется.

## 2. Что уже есть в коде (не изобретать заново)

| Компонент | Путь | Роль |
|---|---|---|
| Polling worker | `infrastructure/polling/PrintSrvPollingRuntime.java` | На каждый инстанс — virtual thread, опрашивает устройства, сохраняет снапшоты и публикует `PrintSrvInstancePolledEvent` |
| Поллер инстанса | `infrastructure/polling/PrintSrvInstancePoller.java` | `queryAll(device)` → `DeviceSnapshot` → `snapshotRepo.save(...)`; при потере связи очищает снапшоты инстанса |
| Хранилище снапшотов | `application/ports/InstanceSnapshotRepository.java` (+ `infrastructure/store/*`) | `get(instanceId, deviceName)` → последний `DeviceSnapshot` или `null` |
| Доменная модель | `domain/model/DeviceSnapshot.java`, `UnitSnapshot.java`, `UnitProperties.java` | `UnitProperties.getCommand()` → `Optional<Integer>` — тег `command` уже распарсен в `Integer` |
| WS-рассылка | `infrastructure/ws/StatusBroadcaster.java` | Слушает `NotificationStateChangedEvent` → обновляет projection store → `liveWsHandler.broadcastNotification(...)` → фронт |
| Сервис уведомлений | `services/NotificationService.java` | Жизненный цикл «последней партии»: `toggleNotification` (USER), `toggleMachineNotification` (MACHINE, `creatorId = printsrvInstanceId`) |
| Доменная модель уведомления | `domain/model/ProductionNotification.java` | `activateAsMachine(unitId, machineId)` — создатель «СКАДА»; `deactivate()` — снятие |
| Событие | `services/NotificationStateChangedEvent.java` | Публикуется при активации/дезактивации |
| REST (справочно) | `api/controller/NotificationController.java` | `GET/POST /line/{unitId}/last-batch` — фронт уже умеет читать состояние |
| Мок PrintSrv | `infrastructure/integration/printsrv/mock/*` (dev-профиль) | XML-снимки `mock-snapshots/{instance}/Line___Unit0.xml`, симулятор `MockStateSimulator.tickLine()` |

Важно: фронт уже получает «последнюю партию» через тот же `NOTIFICATION`-канал
(`/ws/live`), поэтому **контракты API и WS не меняются**.

## 3. Допущения (зафиксированы, при расхождении — стоп и вопрос)

1. «Поле CMD» = тег `command` устройства `Line` в ответе `QueryAll`
   (в XML-снимках и `PropertiesDTO` ключ называется `command`; скрипт PrintSrv
   дублирует его в `message` строкой). Сравнение — по целочисленному
   `UnitProperties.getCommand()`.
2. Код сигнала = `113` (вынести в конфиг, значение по умолчанию `113`).
3. Имя устройства = `Line` (вынести в конфиг, значение по умолчанию `Line`).
4. Юниты устройства `Line` в снапшоте: смотрим **все** юниты (в реальности это
   `u1`); сигнал активен, если `command == 113` хотя бы в одном юните.
5. **Уровневая семантика**: сигнал активен ровно тогда, когда в поле `113`.
   - Появление `113` при отсутствии активного уведомления → активация
     (однократная; застрявший `113` не спамит повторными активациями).
   - Исчезновение `113` (значение другое, тег пропал, снапшот недоступен) →
     снятие **только того** уведомления, которое поставил этот автомат
     (`creatorType = MACHINE`, `creatorId = instanceId`). Уведомление,
     установленное работником (USER), детектор **не трогает никогда**.
6. Если работники закрыли машинное уведомление (complete/cancel), а `113`
   всё ещё в поле → на следующем poll-цикле детектор активирует его заново.
   Это осознанное следствие уровневой семантики («стоит, пока 113 есть»).
7. Снапшот инстанса недоступен (connection lost, репозиторий очищен поллером)
   трактуется как «сигнала нет» → свою MACHINE-уведомление детектор снимает.
   Это защита от «зависшего» уведомления при обрыве связи.
8. `unitId` уведомления = `instanceId` инстанса (так же, как у
   `toggleMachineNotification`).
9. Персистентность: состояние уведомления хранится в БД
   (`production_notifications`), поэтому перезапуск бэкенда сам по себе
   ничего не ломает: при старте детектор сверяет актуальное `command` с
   состоянием в БД и приводит их друг к другу за один poll-цикл.

## 4. План реализации (агент №1)

### 4.1 Конфигурация — `config/PrintSrvProperties.java`

Добавить вложенный блок (значения по умолчанию, без обязательности в yaml):

```java
public static class BatchEndProperties {
    private String deviceName = "Line";   // устройство-источник сигнала
    private int commandCode = 113;        // код «последняя партия»
}
// в PrintSrvProperties: private BatchEndProperties batchEnd = new BatchEndProperties();
```

В `application.yaml` добавить документированный блок:

```yaml
printsrv:
  batch-end:
    device-name: Line
    command-code: 113
```

### 4.2 Детектор — новый Spring-компонент

`services/LineBatchEndDetector.java`:

```java
@Component
public class LineBatchEndDetector {

    // deps: InstanceSnapshotRepository, NotificationService, PrintSrvProperties

    @EventListener
    public void onInstancePolled(PrintSrvInstancePolledEvent event) {
        String instanceId = event.instanceId();
        try {
            DeviceSnapshot snapshot = snapshotRepo.get(instanceId, batchEnd.getDeviceName());
            boolean signalActive = snapshot != null && snapshot.units().values().stream()
                    .map(u -> u.properties().getCommand().orElse(null))
                    .anyMatch(code -> code != null && code == batchEnd.getCommandCode());

            if (signalActive) {
                notificationService.activateMachineNotificationIfAbsent(instanceId, instanceId);
            } else {
                notificationService.deactivateMachineNotificationIfPresent(instanceId, instanceId);
            }
        } catch (Exception ex) {
            // Детектор не должен ломать polling: лог WARN, без проброса.
        }
    }
}
```

Ключевые свойства:

- **Идемпотентность**: каждый poll-цикл детектор приводит состояние
  уведомления к уровню сигнала. Повторные вызовы при неизменном уровне —
  дешёвые no-op (сервис сам сравнивает с БД), логировать no-op не нужно
  (иначе засорение логов каждые 5 секунд на каждый инстанс).
- Отдельный in-memory state-tracker для edge-детекции **не нужен** —
  источником истины является значение в снапшоте + состояние в БД.
- Любое исключение из сервиса/репозитория ловится и логируется WARN-ом:
  падение детектора не должно ронять polling worker.

### 4.3 Методы активации/дезактивации — `services/NotificationService.java`

Добавить два метода (НЕ переисользовать `toggleMachineNotification` — toggle
при повторном вызове снял бы своё же уведомление, а нам нужна уровневая
идемпотентность):

```java
/**
 * Активация «последней партии» от СКАДА (сигнал из polling-потока).
 * Идемпотентна: при уже активном уведомлении любого создателя — no-op.
 *
 * @return true, если уведомление было создано этим вызовом
 */
public boolean activateMachineNotificationIfAbsent(@NonNull String unitId, @NonNull String machineId) {
    ProductionNotification existing = notificationRepository.findActiveByUnitId(unitId).orElse(null);
    if (existing != null) {
        return false;   // любое активное уведомление (USER или MACHINE) — не дублируем
    }
    activate(unitId, ProductionNotification.activateAsMachine(unitId, machineId), machineId);
    log.info("Batch-end signal: machine notification ACTIVATED unitId='{}'", unitId);
    return true;
}

/**
 * Снятие «последней партии», поставленной автоматом (сигнал из polling-потока).
 * Снимает ТОЛЬКО уведомление с creatorType=MACHINE и creatorId=machineId.
 * Уведомление работника (USER) и чужие MACHINE — не трогает.
 *
 * @return true, если уведомление было снято этим вызовом
 */
public boolean deactivateMachineNotificationIfPresent(@NonNull String unitId, @NonNull String machineId) {
    ProductionNotification existing = notificationRepository.findActiveByUnitId(unitId).orElse(null);
    if (existing == null) {
        return false;
    }
    if (existing.creatorType() != NotificationCreatorType.MACHINE
            || !existing.creatorId().equals(machineId)) {
        log.debug("Batch-end off ignored: unitId='{}' active by '{}' ({}) — not ours",
                unitId, existing.creatorId(), existing.creatorType());
        return false;
    }
    deactivate(unitId, existing, machineId);
    log.info("Batch-end signal: machine notification DEACTIVATED unitId='{}'", unitId);
    return true;
}
```

`activate(...)` и `deactivate(...)` — существующие приватные методы
(сохраняют и публикуют `NotificationStateChangedEvent`), изменений в них
не требуется.

### 4.4 Где логировать

- `INFO` при фактической активации/дезактивации (unitId, instanceId).
- `DEBUG` при игноре «не наше / уже активно».
- `WARN` при неожиданном исключении детектора.
- Сообщения — на русском, в стиле существующих логов (`Notification activated: ...`).

### 4.6 Багфикс в `PrintSrvInstancePoller` (найдено тестированием)

Для выполнения критерия приёмки 5 пришлось изменить поведение поллера:
поле `wasReachable` инициализируется `true` вместо `false` (с обоснованием
в javadoc). Иначе при старте бэкенда с недоступным инстансом первый
failed-poll не считался изменением доступности, `PrintSrvInstancePolledEvent`
не публиковалось, и детектор не снимал «вечное» MACHINE-уведомление.
Регрессионный тест: `infrastructure/polling/PrintSrvInstancePollerTest.java`.

### 4.7 Что НЕ делать

- Не трогать machine-JWT, `MachineRegistrationController`, `SecurityConfig`.
- Не менять формат `NOTIFICATION`-сообщения WS, REST-контракты, DTO фронта.
- Не снимать и не пересоздавать уведомления, установленные работником (USER).
- Не вводить in-memory edge-трекеры: уровень сигнала — в снапшоте,
  уровень уведомления — в БД, детектор их только сводит.
- Не писать новых миграций БД (таблица `production_notifications` уже есть).
- Задача бэкенд-only: ничего во фронтенде не меняем.

### 4.8 Проверка реализатором до сдачи

1. `./gradlew compileJava` — сборка без ошибок.
2. `./gradlew test` — существующие тесты зелёные.
3. Ручной дымовой прогон (dev, мок): временно выставить `command=113` в
   `mock-snapshots/{instance}/Line___Unit0.xml` → в логе
   `Batch-end signal: machine notification ACTIVATED`; вернуть `0` →
   `DEACTIVATED`. Исходные мок-снимки не коммитить изменёнными.

## 5. План тестирования (агент №2, после реализации агентом №1)

Тесты — на JUnit 5 (`spring-boot-starter-test` уже подключён), стиль —
как в `src/test/.../services/NotificationServiceTest.java` (AssertJ, Mockito).

### 5.1 Юнит-тесты детектора (без Spring)

Файл: `src/test/java/.../services/LineBatchEndDetectorTest.java`.

Моки (Mockito): `InstanceSnapshotRepository`, `NotificationService`.
Публиковать события напрямую: `detector.onInstancePolled(new PrintSrvInstancePolledEvent(instanceId))`.

Кейсы:
| # | Сценарий | Ожидание |
|---|---|---|
| 1 | `command: 0 → 113` | вызван `activate...IfAbsent` 1 раз, `deactivate...` не вызывался |
| 2 | `113 → 113 → 113` (несколько poll) | activate вызван каждый раз (идемпотентность — на совести сервиса), деактиватор не вызывался |
| 3 | `113 → 0` | вызван `deactivateMachineNotificationIfPresent` 1 раз |
| 4 | `0 → 0` | deactivate вызывается (уровневая сверка), activate не вызывался |
| 5 | snapshot == null (устройство недоступно) | вызван deactivate (снятие «зависшего» сигнала), activate не вызывался, без исключений |
| 6 | `command == null` (тег отсутствует) | deactivate вызван, activate не вызывался |
| 7 | код `!= 113` (например, 555) | activate не вызывался, deactivate вызван |
| 8 | `NotificationService` кидает исключение | исключение не пробрасывается наружу (детектор ловит), polling не падает |
| 9 | два юнита в снапшоте, хотя бы один `113` | activate вызван, deactivate не вызывался |
| 10 | разные instanceId изолированы | срабатывание на одном не влияет на другой |
| 11 | устройство в снапшоте называется не `Line` (другой deviceName) | `snapshotRepo.get` вернул null → поведение как в кейсе 5 |

Для построения `DeviceSnapshot`/`UnitSnapshot`/`UnitProperties` —
существующие конструкторы/билдеры (`UnitProperties.builder().command(...)`).

### 5.2 Юнит-тесты новых методов `NotificationService`

Файл: дополнить `NotificationServiceTest.java` (новый `@Nested`-класс).
Моки: `NotificationRepository`, `UserAssignmentRepository`,
`ApplicationEventPublisher` (по образцу существующих тестов).

`activateMachineNotificationIfAbsent`:
1. Нет активного → создаётся `MACHINE`-уведомление с `creatorId = machineId`,
   публикуется `NotificationStateChangedEvent` типа `ACTIVATED`, `true`.
2. Уже активно этим же MACHINE → `false`, никаких save/publish.
3. Уже активно USER → `false`, никаких save/publish (не дублируем чужое).

`deactivateMachineNotificationIfPresent`:
4. Активно этим же MACHINE → снято, событие `DEACTIVATED`, `true`.
5. Активно USER → `false`, без save/publish (чужое не снимаем — ключевой
   инвариант, регрессионный тест).
6. Активно другим MACHINE → `false`, без save/publish.
7. Нет активного → `false`, без save/publish.

### 5.3 Интеграционный тест (опционально, если инфраструктура позволяет)

`@SpringBootTest` с dev/mock-профилем: изменить `command` в состоянии мока
(XML-база снимков / `MockInstanceState`), дождаться poll-цикла, проверить:
- при `113` в `production_notifications` появилась активная запись
  (`creator_type = MACHINE`), `GET /api/v1.0.0/line/{unitId}/last-batch`
  отдаёт `active: true`;
- при возврате `0` запись снята (`active: false`);
- в `ActiveNotificationStore` projection синхронна.

Если полноценный `@SpringBootTest` тяжело поднять в имеющемся каркасе —
заменить на «расширенный юнит» с реальным in-memory репозиторием, это приемлемо.

### 5.4 Ручное E2E (dev-профиль, мок PrintSrv)

1. `make back-run && make back-wait`.
2. Поднять фронт (`make front-dev`) или подключиться к `/ws/live` WS-клиентом.
3. Выставить `command=113` в `mock-snapshots/{instance}/Line___Unit0.xml`
   (или форсировать через `MockStateSimulator`, если умеет менять `command`
   в `tickLine`).
4. Проверить: в `make back-logs` — `Batch-end signal: machine notification
   ACTIVATED`; на фронте/в WS — `NOTIFICATION` с создателем «СКАДА»;
   `GET /line/{unitId}/last-batch` → `active: true`, `creatorType: MACHINE`.
5. **Держать `113`**: несколько poll-циклов → повторных активаций нет,
   уведомление остаётся активным (ровно одно).
6. Вернуть `0`: лог `DEACTIVATED`, на фронте уведомление снято,
   `last-batch` → `active: false`.
7. Сценарий «работник вместо автомата»: через UI фронта поставить
   «последнюю партию» (USER), затем погонять `113`/`0` в моке → USER-уведомление
   детектор не снимает и не дублирует.
8. Сценарий «обрыв связи»: добавить инстанс в `offline-instances` dev-конфига
   → детектор снимает своё MACHINE-уведомление (защита от зависшего).
9. Вернуть мок-файлы/конфиг в исходное состояние; `make back-stop`.

### 5.5 Регрессия

- `./gradlew test` — весь существующий + новый набор зелёный.
- Штатный toggle `POST /line/{unitId}/last-batch` (USER и MACHINE-JWT)
  работает как раньше: ручное снятие машинного уведомления возможно,
  но при persist `113` детектор его восстановит на следующем цикле
  (осознанное поведение, см. допущение 6).

## 6. Критерии приёмки

1. Пока `Line.command == 113` — на фронте активно уведомление «последняя
   партия» с создателем «СКАДА» (MACHINE); повторных рассылок нет.
2. Когда `113` исчезает — уведомление снимается само, без участия работников.
3. Уведомления работников (USER) детектор никогда не снимает и не дублирует.
4. Падения детектора/сервиса не роняют polling worker.
5. Потеря связи с инстансом не оставляет «вечное» MACHINE-уведомление.
6. Существующие тесты и ручные сценарии toggle не сломаны.
7. Новые тесты из 5.1–5.2 зелёные; E2E-чеклист 5.4 пройден.

---

## 7. Миграция на счётчик `FinishBatch` (markserver-libs PR #43, 2026-09-21)

Исходная уровневая схема (`Line.command == 113`) заменена по решению
руководства на отдельное поле: markserver при получении команды
`LINE_CMD_FINISH_BATCH (113)` инкрементирует счётчик в свойстве `FinishBatch`
устройства `Line` (значение циклически 1→100→1), код в `CMD` не задерживается.

Изменения в бэкенде:

- `LineBatchEndDetector` переведён на событийную (edge) семантику: срабатывание
  — по изменению значения `FinishBatch` между poll-циклами; первое наблюдение
  после старта/потери снапшота — только baseline, без активации.
- Авто-снятие уведомления: счётчик не несёт сигнала «конец», поэтому
  MACHINE-уведомление снимается по TTL `printsrv.batch-end.notification-ttl-minutes`
  (по умолчанию 10 минут) либо при потере снапшота/свойства.
- Конфигурация: `batch-end.command-code` удалён, добавлены
  `batch-end.property-name` (по умолчанию `FinishBatch`) и
  `batch-end.notification-ttl-minutes`.
- Значение `FinishBatch` приходит в `rawProperties` снапшота
  (`PrintSrvMapper`), именованное поле в `UnitProperties` не требуется.

Инварианты сохранены: USER-уведомления работников детектор не снимает и не
дублирует; обрыв связи не оставляет «зависший» сигнал; падения детектора не
роняют polling worker.
