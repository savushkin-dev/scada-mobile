# Источники данных для фронтенда

## Что это за документ

Документ фиксирует, откуда именно backend может брать данные для контракта из [FRONTEND_API.md](FRONTEND_API.md), если ориентироваться на подтвержденные источники PrintSrv и на карту соответствий из внешнего артефакта `GRUNW11_SCADA_OBJECT_MAPPING.md` (файл не хранится в этом репозитории).

Текущая база анализа:

- аппарат `Grunw11`
- frontend-контракт из [FRONTEND_API.md](FRONTEND_API.md)
- карта SCADA/PrintSrv-источников из внешнего артефакта `GRUNW11_SCADA_OBJECT_MAPPING.md` (вне текущего репозитория)

Документ отвечает на вопрос: какие поля фронта можно брать напрямую, какие нужно агрегировать, а какие пока не подтверждены по данным Grunw11 и требуют отдельного решения в backend.

## Важная оговорка по области применимости

Этот документ нельзя читать как универсальную схему для всех аппаратов.

Он построен на детальном анализе именно `Grunw11`, а `Grunw11` в составе устройств заметно богаче многих других аппаратов. По реестру аппаратов уже видно, что набор устройств в проекте отличается от линии к линии:

- есть аппараты с одним принтером, например `Hassia1`, `Hassia2`
- есть аппараты с двумя принтерами, например `Bosch`, `Grunw1`, `Hassia3`
- есть аппараты с четырьмя принтерами и расширенным набором камер, как `Grunw11`
- есть аппараты с другим типом камер вообще, например `trepko1`, `trepko2`

Из этого следует главное правило интеграции:

- количество камер и принтеров зависит от аппарата
- список unit-объектов в PrintSrv зависит от аппарата
- набор агрегированных полей в `scada` тоже зависит от аппарата

Например, если на линии нет второй камеры агрегации, то backend не должен ожидать наличие `CamAgregation2`, `Dev043...`, `Dev044...` и связанных с ними полей вроде `Dev043CounterGeneral` или `Dev044Error`. Аналогично, если на аппарате только один принтер, то не будет ни `Printer12`, ни `LineDev012...`.

Поэтому все примеры ниже надо читать так:

- для `Grunw11` подтверждено именно в таком виде
- для других аппаратов backend обязан сначала определить фактический состав устройств, а уже потом строить topology, `DEVICES_STATUS`, `ALERT` и другие ответы

---

## Главный вывод

Для текущего минимального фронтового API источники распадаются на четыре группы:

1. Статическая топология приложения.
2. Данные партии и очереди из `BatchQueue`.
3. Состояние линии из `Line`.
4. Диагностика камер и принтеров из `scada`, плюс прямые поля из `PrinterXX` и `Cam...`.

Практическое правило для backend:

- все, что относится к партии, брать в первую очередь из `BatchQueue`
- все, что относится к line-level состоянию, брать из `Line`
- все, что относится к агрегированной диагностике камер и принтеров, брать из `scada`
- прямые поля принтеров и камер брать из их unit-файлов только там, где `scada` не содержит нужной информации

Но это правило работает только после шага discovery:

- сначала backend определяет, какие устройства реально присутствуют у данного аппарата
- затем проверяет, какие агрегированные поля реально существуют для этого состава устройств
- и только потом мапит их в frontend DTO

---

## Подтвержденные backend-источники для Grunw11

Ниже перечислено то, что подтверждено именно для `Grunw11`. Для другого аппарата этот список может быть короче, длиннее или содержать устройства других типов.

### Реальные unit-источники

- `BatchQueue`
- `Line`
- `scada`
- `Printer11`
- `Printer12`
- `Printer13`
- `Printer14`
- `CamAgregation1`
- `CamAgregation2`
- `CamAgregationBox1`
- `CamAgregationBox2`
- `CamChecker1`
- `CamChecker2`
- `CamEanChecker1`
- `CamEanChecker2`
- `CamEanChecker3`
- `CamEanChecker4`

### Что делает `scada`

Для `Grunw11` агрегатор `scada` генерирует для фронта подтвержденные диагностические теги:

- `Dev041...`
- `Dev042...`
- `Dev043...`
- `Dev044...`
- `Dev071...`
- `Dev072...`
- `Dev073...`
- `Dev074...`
- `LineDev011...`
- `LineDev012...`
- `LineDev013...`
- `LineDev014...`
- `lineerr`

Важно:

- это не универсальный список полей `scada` для всех аппаратов
- префиксы `Dev041...`, `Dev042...`, `Dev043...`, `Dev044...` появляются только там, где в конфигурации аппарата реально есть соответствующие устройства и маппинг в `scada`
- то же самое относится к `LineDev011...`, `LineDev012...`, `LineDev013...`, `LineDev014...`: число этих групп зависит от числа принтеров на линии

### Маппинг устройств в SCADA-диагностику

Камеры:

- `CamAgregation1 -> Dev041`
- `CamAgregationBox1 -> Dev042`
- `CamAgregation2 -> Dev043`
- `CamAgregationBox2 -> Dev044`
- `CamEanChecker1 -> Dev071`
- `CamEanChecker2 -> Dev072`
- `CamEanChecker3 -> Dev073`
- `CamEanChecker4 -> Dev074`

Принтеры:

- `Printer11 -> LineDev011`
- `Printer12 -> LineDev012`
- `Printer13 -> LineDev013`
- `Printer14 -> LineDev014`

---

## 1. REST topology

### GET `/api/v1.0.0/workshops/topology`

Источник:

- не PrintSrv
- backend-конфигурация приложения

Причина:

- в анализе Grunw11 нет данных о цехах как о сущности верхнего уровня
- PrintSrv знает устройство и линию, но не прикладную мобильную иерархию цехов

Рекомендация:

- хранить `workshops` в YAML/БД backend

Поля:

| Поле API | Откуда брать |
|---|---|
| `id` | backend config |
| `name` | backend config |
| `totalUnits` | backend config или расчет по unit topology |

### GET `/api/v1.0.0/workshops/{workshopId}/units/topology`

Источник:

- не PrintSrv напрямую
- backend-конфигурация приложения

Поля:

| Поле API | Откуда брать |
|---|---|
| `id` | backend config |
| `workshopId` | backend config |
| `unit` | backend config |

### GET `/api/v1.0.0/workshops/{workshopId}/units/{unitId}/devices/topology`

Источник:

- `Line___Unit0.xml`
- группировка по именам устройств

Для Grunw11 подтверждено:

- `Level1Printers = Printer11,Printer12,Printer13,Printer14`
- `LineDevices = CamChecker1,CamChecker2,CamAgregation1,CamAgregation2,CamAgregationBox1,CamAgregationBox2,CamEanChecker1,CamEanChecker2,CamEanChecker3,CamEanChecker4,Printer11,Printer12,Printer13,Printer14`

Рекомендуемая сборка ответа для Grunw11:

| Поле API | Источник |
|---|---|
| `devices.printers` | `Line.Level1Printers` |
| `devices.aggregationCams` | фильтр `Line.LineDevices` по `CamAgregation1`, `CamAgregation2` |
| `devices.aggregationBoxCams` | фильтр `Line.LineDevices` по `CamAgregationBox1`, `CamAgregationBox2` |
| `devices.checkerCams` | фильтр `Line.LineDevices` по `CamChecker1`, `CamChecker2`, `CamEanChecker1..4` |

Примечание:

- `CamEanChecker1..4` не имеют отдельной группы в текущем контракте frontend и логично попадают в `checkerCams`
- для другого аппарата backend не должен хардкодить этот набор; его нужно собирать из фактического `Line.LineDevices` и связанных unit-конфигов

---

## 2. WebSocket `/ws/unit/{unitId}`

## `LINE_STATUS`

Источник для каждого поля:

| Поле API | Рекомендуемый источник | Комментарий |
|---|---|---|
| `lineName` | backend topology config | Лучше брать отображаемое имя аппарата, а не имя `.mpr` файла |
| `lineState` | `Line.ST` | Подтвержденный источник |
| `shortCode` | `BatchQueue.kmc`, fallback `Printer11.kmc` | В текущей документации это наиболее близкий подтвержденный источник короткого кода продукта |
| `description` | `BatchQueue.description`, fallback `Printer11.descr` | Для линии первичнее `BatchQueue`, т.к. это текущая партия |
| `ean13` | `BatchQueue.ean13`, fallback `Printer11.ean13` | Аналогично |
| `batchNumber` | `BatchQueue.batch`, fallback `Printer11.partynumber` | `BatchQueue.batch` лучше отражает текущую партию линии |
| `dateProduced` | `BatchQueue.dateproduced`, fallback `Printer11.dateproduced` | Подтверждено |
| `datePacking` | `BatchQueue.datepack`, fallback `Printer11.datepack` | Подтверждено |
| `dateExpiration` | `BatchQueue.dateexpiration`, fallback `Printer11.dateexpiration` | Подтверждено |
| `initialCounter` | требует уточнения, временно `Printer11.curitem` с парсингом первого сегмента | В Grunw11 нет отдельного подтвержденного поля именно начального счетчика; `BatchQueue.begincounter` это флаг, а не само числовое значение |
| `site` | `BatchQueue.place`, fallback `Printer11.place` | Семантически это место/слот на линии |
| `itf` | `BatchQueue.itf`, fallback `Printer11.itf` | Подтверждено |
| `capacity` | `BatchQueue.emk`, fallback `Printer11.emk` | В документации подтверждено как количество единиц в коробе |
| `boxCount` | требует бизнес-уточнения, кандидаты `BatchQueue.kole` или `BatchQueue.kolm` | По подтвержденной документации `kole` = количество единиц, `kolm` = количество маркировок; название `boxCount` не совпадает с семантикой 1:1 |
| `packageCount` | требует бизнес-уточнения, кандидаты `BatchQueue.kolm` или `BatchQueue.kole` | Аналогично |
| `freeze` | `BatchQueue.frozen` | Подтверждено |
| `region` | `BatchQueue.region` | Подтверждено |
| `design` | `BatchQueue.designe`, fallback `Printer11.designe` | Подтверждено |
| `printDM` | `BatchQueue.printdm`, fallback `Printer11.printdm` | Подтверждено |

Итог по `LINE_STATUS`:

- почти весь блок можно собрать из `BatchQueue` + `Line`
- `Printer11` нужен как fallback или как источник отдельных принтерных деталей
- поля `initialCounter`, `boxCount`, `packageCount` сейчас семантически неоднозначны и должны быть подтверждены отдельно
- наличие fallback-полей в `Printer11` тоже зависит от аппарата: на некоторых линиях принтеров может быть больше одного, а на некоторых только один

---

## `DEVICES_STATUS`

Это самый аппаратозависимый блок во всем контракте. Здесь нельзя предполагать фиксированное количество устройств.

Для одного аппарата массив `printers[]` может содержать один объект, для другого два, для третьего четыре и более. То же касается камер: где-то будут только `CamAgregation` и `CamChecker`, где-то добавятся `CamEanChecker1..4`, а где-то набор устройств будет вообще другой.

Следствие для backend:

- размеры массивов `printers`, `aggregationCams`, `aggregationBoxCams`, `checkerCams` должны определяться по topology конкретного аппарата
- список полей `scada`, доступных для этих массивов, должен выводиться из фактического device-to-scada mapping, а не из предположения, что всегда существуют `Dev041...Dev044` или `LineDev011...LineDev014`

### Принтеры

Рекомендуемая схема:

| Поле API | Источник | Комментарий |
|---|---|---|
| `printers[].deviceName` | topology | `Printer11..14` |
| `printers[].state` | `PrinterXX.ST` | Прямое состояние принтера |
| `printers[].error` | `scada.LineDev0XXError` | Это именно тот error-слой, который видит SCADA |
| `printers[].batch` | если нужен счетчик: `PrinterXX.kolm`; если нужна строка как в SCADA: `PrinterXX.curitem` | В текущем фронтовом описании поле названо как счетчик, но в SCADA карточка принтера использует `curitem` |

Практическая рекомендация:

- если фронту нужен именно числовой счетчик, лучше отдать `PrinterXX.kolm`
- если фронту нужно то же, что в SCADA-блоке принтера, лучше переименовать поле и отдавать `PrinterXX.curitem`
- количество принтеров на линии брать не из имени `Printer11`, а из topology аппарата; на другой линии список может закончиться на `Printer11`, `Printer12` или иметь иной состав

### Камеры агрегации на потоке

Для Grunw11 это:

- `CamAgregation1`
- `CamAgregation2`

Рекомендуемая схема:

| Поле API | Источник | Пример для Grunw11 |
|---|---|---|
| `aggregationCams[].deviceName` | topology | `CamAgregation1`, `CamAgregation2` |
| `aggregationCams[].read` | `scada.Dev041CounterGeneral`, `scada.Dev043CounterGeneral` | агрегированная статистика SCADA |
| `aggregationCams[].unread` | `scada.Dev041CounterMissing`, `scada.Dev043CounterMissing` | агрегированная статистика SCADA |
| `aggregationCams[].state` | `scada.Dev041Work`, `scada.Dev043Work`, fallback direct `Cam...ST` | для UI ближе именно `Work`-флаг из `scada` |
| `aggregationCams[].error` | `scada.Dev041Error`, `scada.Dev043Error` | агрегированная ошибка |

Важно:

- это пример именно для `Grunw11`
- на аппарате с одной камерой агрегации будет только одна группа полей такого типа
- backend должен строить соответствие не по номерам `041` и `043`, а по реальному маппингу устройства в `scada`

### Камеры агрегации на коробе

Для Grunw11 это:

- `CamAgregationBox1`
- `CamAgregationBox2`

Рекомендуемая схема:

| Поле API | Источник | Пример для Grunw11 |
|---|---|---|
| `aggregationBoxCams[].deviceName` | topology | `CamAgregationBox1`, `CamAgregationBox2` |
| `aggregationBoxCams[].read` | `scada.Dev042CounterGeneral`, `scada.Dev044CounterGeneral` | |
| `aggregationBoxCams[].unread` | `scada.Dev042CounterMissing`, `scada.Dev044CounterMissing` | |
| `aggregationBoxCams[].state` | `scada.Dev042Work`, `scada.Dev044Work`, fallback direct `Cam...ST` | |
| `aggregationBoxCams[].error` | `scada.Dev042Error`, `scada.Dev044Error` | |

Важно:

- на других аппаратах камеры коробочной агрегации могут отсутствовать совсем
- соответственно, могут отсутствовать и группы полей `Dev042...` или `Dev044...`

### Checker-камеры

Для Grunw11 в `checkerCams` логично включать:

- `CamChecker1`
- `CamChecker2`
- `CamEanChecker1`
- `CamEanChecker2`
- `CamEanChecker3`
- `CamEanChecker4`

Но источник данных здесь неоднородный.

И это еще один аппаратозависимый участок:

- на части аппаратов есть только `CamChecker`
- на части есть и `CamChecker`, и `CamEanChecker1..4`
- на части аппаратов может не быть `CamEanChecker` вообще

#### EAN-checker камеры

Для `CamEanChecker1..4` использовать тот же подход, что и для SCADA:

| Поле API | Источник |
|---|---|
| `deviceName` | topology |
| `read` | `scada.Dev071..074CounterGeneral` |
| `unread` | `scada.Dev071..074CounterMissing` |
| `state` | `scada.Dev071..074Work` |
| `error` | `scada.Dev071..074Error` |

Но только если такие устройства вообще существуют на данном аппарате. Для аппаратов без `CamEanChecker1..4` этих полей в `scada` тоже не будет.

#### CamChecker1 и CamChecker2

Для `CamChecker1/2` отдельного `Dev0XX`-слоя в `scada` для Grunw11 не подтверждено.

Подтвержденные прямые поля в самих unit-файлах:

- `ST`
- `BatchSucceeded`
- `BatchFailed`
- `message`
- `LastReadTime`

Рекомендуемая схема:

| Поле API | Источник | Комментарий |
|---|---|---|
| `deviceName` | topology | |
| `read` | `CamCheckerX.BatchSucceeded` | прямой device-source |
| `unread` | `CamCheckerX.BatchFailed` | прямой device-source |
| `state` | `CamCheckerX.ST` | прямой device-source |
| `error` | backend-вычисление из runtime `err` или из alert-service | отдельного подтвержденного `scada.Dev...Error` для CamChecker1/2 нет |

Итог по `DEVICES_STATUS`:

- для принтеров и камер с Dev-мэппингом основным источником диагностики должен быть `scada`
- для `CamChecker1/2` backend должен брать прямые счетчики из device-unit и сам вычислять error-флаг
- количество объектов в каждом массиве и набор доступных `scada`-полей должны вычисляться динамически по конкретному аппарату

---

## `QUEUE`

Источник:

- `BatchQueue`
- при возможности лучше опираться на структурированное представление очереди, а не только на display-строки

Подтвержденные поля:

- `Item01..Item10`
- `Item01Selected..Item10Selected`
- `CurItem`

Дополнительный источник, если backend умеет читать его отдельно:

- `Config/Default/Files/BatchQueue.json`

Рекомендуемая схема:

| Поле API | Источник | Комментарий |
|---|---|---|
| `items[].position` | индекс `Item01..Item10` | подтверждено |
| `items[].shortCode` | предпочтительно структурированный парсинг из `BatchQueue.json`; fallback парсинг `ItemXX` | `ItemXX` это display-строка, формат может быть нестрогим |
| `items[].batch` | предпочтительно `BatchQueue.json`; fallback парсинг `ItemXX` | |
| `items[].dateProduced` | предпочтительно `BatchQueue.json`; fallback парсинг `ItemXX` | |

Практическая рекомендация:

- если backend имеет доступ к `BatchQueue.json`, это более надежный источник для `QUEUE`, чем парсинг строк `Item01..Item10`

---

## `ERRORS`

Этот блок не собирается из одного XML-файла.

Он требует backend-агрегации из нескольких слоев:

1. runtime-состояние устройств
2. `scada`-теги ошибок
3. состояние линии
4. журнал событий

### `deviceErrors[]`

Рекомендуемые источники:

| Поле API | Откуда брать | Комментарий |
|---|---|---|
| `objectName` | реальное device-name: `Printer11`, `CamAgregation1`, `Line` и т.д. | лучше использовать backend-имя устройства, не SCADA-имя виджета |
| `propertyDesc` | backend mapping table по флагу ошибки | например `LineDev011Error`, `Dev041Dublicate`, `Dev071Fail` |
| `value` | текущее значение error-флага | обычно `1`/`0` |
| `description` | backend human-readable message | из словаря ошибок или описания состояния |

Для Grunw11 подтвержденные error-источники:

- `scada.LineDev011Error..LineDev014Error`
- `scada.Dev041Error..Dev044Error`
- `scada.Dev071Error..Dev074Error`
- `Line.Error`
- `Line.ErrorMessage`

Для других аппаратов этот список должен пересчитываться по фактическому составу устройств. Нельзя считать, что если в коде для `Grunw11` есть `Dev041Error` или `LineDev014Error`, то такие же поля будут у любой другой линии.

Для `CamChecker1/2` error-флаг нужно вычислять backend-ом из runtime-состояния камеры, так как отдельного `Dev`-агрегата не подтверждено.

### `logs[]`

По анализу Grunw11 и существующей документации наиболее реалистичные источники:

- `sqlite_logs.db`, если backend использует его как журнал событий
- либо файлы логов PrintSrv, если backend читает их напрямую

На уровне минимального фронтового контракта это backend-агрегируемый слой, а не поле из одного unit.

---

## 3. WebSocket `/ws/live`

## `UNITS_STATUS`

### `payload[].event`

Не существует как готовое поле в Grunw11 unit-файлах.

Это backend-производное представление состояния аппарата.

Рекомендуемые входы для вычисления:

- `Line.ST`
- `Line.Error`
- `Line.ErrorMessage`
- `scada.lineerr`

Пример логики:

- если есть ошибка линии или активные device-ошибки, отдавать текст ошибки
- если `Line.ST = 1`, отдавать состояние типа `В работе`
- если `Line.ST != 1` и ошибок нет, отдавать `Стоп` или аналогичный warning-state

### `payload[].timer`

Для Grunw11 в проанализированных PrintSrv-данных подтвержденного прямого поля таймера состояния не найдено.

Рекомендация:

- вычислять backend-ом как время с момента последней смены вычисленного `event`

---

## `ALERT_SNAPSHOT` и `ALERT`

Это не прямой снимок одного устройства, а результат backend-агрегации.

### Какие поля можно брать

| Поле API | Источник |
|---|---|
| `workshopId` | backend topology config |
| `unitId` | backend topology config |
| `unitName` | backend topology config |
| `active` | backend alert engine |
| `timestamp` | backend alert engine |

### Из чего собирать сами алерты

Для Grunw11 подтвержденные источники для alert engine:

- `Line.Error`
- `Line.ErrorMessage`
- `Line.ST`
- `scada.lineerr`
- `scada.LineDev011Error..014Error`
- `scada.Dev041Error..044Error`
- `scada.Dev071Error..074Error`
- runtime `err` для устройств без `scada`-обертки, например `CamChecker1/2`

Для других аппаратов backend должен собирать список alert-источников по фактическим unit-объектам и реально существующим `scada`-полям, а не по фиксированному шаблону `Grunw11`.

### `errors[].device`

Рекомендуется отдавать реальные backend-имена устройств:

- `Printer11`
- `Printer12`
- `CamAgregation1`
- `CamEanChecker1`
- `Line`

Не рекомендуется отдавать:

- `ConnectionPrinter11`
- `CMSDev041`
- `cdev41err`

Причина:

- это экранные объекты SCADA, а не реальные backend-устройства

### `errors[].code`

Источник:

- `Line.Error` или device-specific `message`, если есть код ошибки
- если сообщение warning-класса и числового кода нет, можно отдавать `0`

### `errors[].message`

Источник:

- `Line.ErrorMessage`, если ошибка относится к линии
- backend-словарь интерпретации кодов device/message-флагов
- fallback: человекочитаемое описание сформированного alert-condition

### `severity`

Это backend-вычисляемое поле.

Под текущую модель Grunw11 логично:

- `Critical`, если есть активная ошибка линии или устройства
- `Warning`, если линия не работает, но явной ошибки нет

---

## 4. Что можно брать уже сейчас без спорных мест

Ниже список полей, для которых источник по Grunw11 подтвержден достаточно уверенно.

### Можно забирать сразу

Ниже список полей, которые для `Grunw11` подтверждены уверенно. Этот блок не означает, что те же самые поля обязательно существуют у любого другого аппарата в том же объеме.

- `lineState -> Line.ST`
- `description -> BatchQueue.description`
- `ean13 -> BatchQueue.ean13`
- `batchNumber -> BatchQueue.batch`
- `dateProduced -> BatchQueue.dateproduced`
- `datePacking -> BatchQueue.datepack`
- `dateExpiration -> BatchQueue.dateexpiration`
- `site -> BatchQueue.place`
- `itf -> BatchQueue.itf`
- `capacity -> BatchQueue.emk`
- `freeze -> BatchQueue.frozen`
- `region -> BatchQueue.region`
- `design -> BatchQueue.designe`
- `printDM -> BatchQueue.printdm`
- `printers[].state -> PrinterXX.ST`
- `printers[].error -> scada.LineDev0XXError`
- `aggregationCams[].read/unread/error -> scada.Dev041/043...`
- `aggregationBoxCams[].read/unread/error -> scada.Dev042/044...`
- `ean-checker state/read/unread/error -> scada.Dev071..074...`

### Требуют отдельного согласования

- `initialCounter`
- `boxCount`
- `packageCount`
- `UNITS_STATUS.payload[].event`
- `UNITS_STATUS.payload[].timer`
- `CamChecker1/2.error`
- структура `QUEUE.items[]`, если не читать `BatchQueue.json`

---

## Итоговая рекомендация для backend

Для минимального frontend-контракта backend должен строить ответ из следующих базовых источников:

1. topology config приложения
2. `BatchQueue`
3. `Line`
4. `scada`
5. `Printer11..14`
6. `CamAgregation1..2`
7. `CamAgregationBox1..2`
8. `CamChecker1..2`
9. `CamEanChecker1..4`

Но это перечень для `Grunw11`, а не для всех аппаратов. В общем случае правильный порядок работы такой:

1. определить состав устройств конкретного аппарата
2. определить, какие unit-объекты реально существуют в PrintSrv для этого аппарата
3. определить, какие агрегированные поля реально публикует `scada` для этого состава устройств
4. только после этого собирать frontend DTO

И отдельно должен иметь собственный слой агрегации для:

1. `ALERT` и `ALERT_SNAPSHOT`
2. `UNITS_STATUS.event`
3. `UNITS_STATUS.timer`
4. `ERRORS.deviceErrors[]`
5. `QUEUE.items[]`, если нужна не display-строка, а структурированная очередь

---

## Короткий практический вывод

Если смотреть только на то, что подтверждено по Grunw11, то frontend сейчас лучше всего кормить так:

- карточку партии собирать в первую очередь из `BatchQueue`
- диагностику устройств брать из `scada`
- прямые статусы принтеров брать из `PrinterXX`
- прямые статусы `CamChecker1/2`, где нет `Dev`-обертки, брать из самих camera-unit
- все человекочитаемые event/alert/log представления вычислять в backend, а не пытаться читать как готовые поля PrintSrv

И главное уточнение: это не означает, что на каждом аппарате обязательно будут все эти сущности и все эти поля. У одного аппарата может быть один принтер и одна камера, у другого два принтера и четыре EAN-checker камеры, у третьего иной состав устройств. Поэтому backend должен опираться на discovery конкретного аппарата, а не на фиксированный список полей из `Grunw11`.