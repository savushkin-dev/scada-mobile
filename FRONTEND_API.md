# Параметры API, нужные фронтенду

---

## REST API

### GET `/api/v1.0.0/workshops/topology`

**Запрос:**

| Параметр | Где | Тип | Обязателен |
|---|---|---|---|
| `If-None-Match` | Header | `string` | нет (ETag от предыдущего ответа) |

**Ответ 200:**

| Поле | Тип | Описание |
|---|---|---|
| `[].id` | `string` | Уникальный идентификатор цеха (используется в запросах и подписках WS) |
| `[].name` | `string` | Отображаемое название цеха (например, «Цех №3 — Молочная линия») |
| `[].totalUnits` | `integer` | Общее количество аппаратов/линий в цехе |

**Ответ 304:** тело пустое, данные не изменились.

---

### GET `/api/v1.0.0/workshops/{workshopId}/units/topology`

**Запрос:**

| Параметр | Где | Тип | Обязателен |
|---|---|---|---|
| `workshopId` | Path | `string` | да |
| `If-None-Match` | Header | `string` | нет |

**Ответ 200:**

| Поле | Тип | Описание |
|---|---|---|
| `[].id` | `string` | Уникальный идентификатор аппарата |
| `[].workshopId` | `string` | ID цеха, которому принадлежит аппарат |
| `[].unit` | `string` | Отображаемое название аппарата (например, «HASSIA №6», «Молокопровод») |

**Ответ 304:** тело пустое.

---

### GET `/api/v1.0.0/workshops/{workshopId}/units/{unitId}/devices/topology`

**Запрос:**

| Параметр | Где | Тип | Обязателен |
|---|---|---|---|
| `workshopId` | Path | `string` | да |
| `unitId` | Path | `string` | да |
| `If-None-Match` | Header | `string` | нет |

**Ответ 200:**

| Поле | Тип | Описание |
|---|---|---|
| `unitId` | `string` | Идентификатор аппарата |
| `workshopId` | `string` | Идентификатор цеха |
| `unit` | `string` | Название аппарата |
| `devices.printers` | `string[]` | Имена принтеров маркировки (используются как ключи в `DEVICES_STATUS`) |
| `devices.aggregationCams` | `string[]` | Имена камер агрегации на потоке |
| `devices.aggregationBoxCams` | `string[]` | Имена камер агрегации на коробе |
| `devices.checkerCams` | `string[]` | Имена камер проверки |

**Ответ 304:** тело пустое.

---

## WebSocket `/ws/live`

Один постоянный канал на весь сеанс. Мультиплексирование через подписку на цех.

### Клиент → Сервер

| `action` | Поле | Тип | Когда |
|---|---|---|---|
| `SUBSCRIBE_WORKSHOP` | `workshopId` | `string` | при открытии страницы цеха |
| `UNSUBSCRIBE_WORKSHOP` | — | — | при уходе со страницы цеха |

### Сервер → Клиент

#### `ALERT_SNAPSHOT` — начальный снимок всех активных алертов

Поля в каждом элементе `payload[]`:

| Поле | Тип | Описание |
|---|---|---|
| `type` | `"ALERT"` | Тип сообщения — всегда `"ALERT"` |
| `workshopId` | `string` | ID цеха, к которому относится аппарат с алертом |
| `unitId` | `string \| number` | ID аппарата с алертом |
| `unitName` | `string` | Читаемое название аппарата для отображения |
| `severity` | `"Critical"` | Уровень критичности (сейчас всегда `"Critical"`) |
| `active` | `boolean` | `true` — алерт активен; `false` — устранён |
| `errors[].device` | `string` | Имя устройства на аппарате, выдавшего ошибку (например, `"Printer11"`, `"Line"`) |
| `errors[].code` | `integer` | Числовой код ошибки из PrintSrv (0, если не применимо) |
| `errors[].message` | `string` | Читаемое описание ошибки для отображения в табло |
| `timestamp` | `string` | Время события в ISO 8601 |

#### `UNITS_STATUS` — статус аппаратов цеха

| Поле | Тип | Описание |
|---|---|---|
| `type` | `"UNITS_STATUS"` | Тип сообщения |
| `workshopId` | `string` | ID цеха, для которого пришёл статус |
| `payload[].unitId` | `string` | ID аппарата |
| `payload[].workshopId` | `string` | ID цеха (дублирует верхний уровень, для удобства) |
| `payload[].event` | `string` | Текущее состояние аппарата (например, `"В работе"`, `"Стоп"`, `"Нет данных"`) |

#### `ALERT` — дельта изменения состояния алерта

| Поле | Тип | Описание |
|---|---|---|
| `type` | `"ALERT"` | Тип сообщения |
| `workshopId` | `string` | ID цеха |
| `unitId` | `string \| number` | ID аппарата |
| `unitName` | `string` | Читаемое название аппарата |
| `severity` | `"Critical"` | Уровень критичности |
| `active` | `boolean` | `true` — новый алерт; `false` — алерт снят (аппарат в норме) |
| `errors[].device` | `string` | Имя устройства-источника ошибки |
| `errors[].code` | `integer` | Код ошибки |
| `errors[].message` | `string` | Описание ошибки |
| `timestamp` | `string` | Время события ISO 8601 |

---

## WebSocket `/ws/unit/{unitId}`

Отдельный канал для страницы деталей аппарата. Открывается при входе, закрывается при выходе.

| Параметр | Где | Тип |
|---|---|---|
| `unitId` | Path | `string` |

### Сервер → Клиент

#### `LINE_STATUS` — данные партии и линии

Все поля `string | null | undefined` (числа тоже передаются строками — особенность PrintSrv).

| Поле | Описание |
|---|---|
| `lineName` | Название линии |
| `lineState` | Текущее состояние линии (`"0"` — стоп, `"1"` — работает и т.п.) |
| `shortCode` | Краткий код продукта |
| `description` | Полное описание/наименование продукта |
| `ean13` | Штрих-код EAN-13 |
| `batchNumber` | Номер партии |
| `dateProduced` | Дата производства |
| `datePacking` | Дата упаковки |
| `dateExpiration` | Дата окончания срока годности |
| `initialCounter` | Начальный счётчик партии |
| `site` | Код площадки/сайта производства |
| `itf` | Штрих-код ITF-14 (групповая упаковка) |
| `capacity` | Ёмкость/объём продукта |
| `boxCount` | Количество коробов в партии |
| `packageCount` | Количество единиц в коробе |
| `freeze` | Признак заморозки (`"0"` / `"1"`) |
| `region` | Регион назначения |
| `design` | Код дизайна упаковки |
| `printDM` | Признак печати DataMatrix (`"0"` / `"1"`) |

#### `DEVICES_STATUS` — состояние устройств

Все числовые поля передаются строками (`"0"` / `"1"`). `"1"` = активно/ошибка, `"0"` = норма.

| Поле | Тип | Описание |
|---|---|---|
| `printers[].deviceName` | `string` | Имя принтера (совпадает с именем из `devices.printers` в topology) |
| `printers[].state` | `string \| null` | Состояние принтера (`"1"` = работает) |
| `printers[].error` | `string \| null` | Код ошибки принтера (`"1"` = есть ошибка) |
| `printers[].batch` | `string \| null` | Счётчик отпечатанных единиц в текущей партии |
| `aggregationCams[].deviceName` | `string` | Имя камеры агрегации на потоке |
| `aggregationCams[].read` | `string \| null` | Количество успешно считанных кодов |
| `aggregationCams[].unread` | `string \| null` | Количество нечитаемых кодов |
| `aggregationCams[].state` | `string \| null` | Состояние камеры |
| `aggregationCams[].error` | `string \| null` | Признак ошибки камеры |
| `aggregationBoxCams[].deviceName` | `string` | Имя камеры агрегации на коробе |
| `aggregationBoxCams[].read` | `string \| null` | Считанные коды |
| `aggregationBoxCams[].unread` | `string \| null` | Нечитаемые коды |
| `aggregationBoxCams[].state` | `string \| null` | Состояние камеры |
| `aggregationBoxCams[].error` | `string \| null` | Признак ошибки |
| `checkerCams[].deviceName` | `string` | Имя камеры проверки |
| `checkerCams[].read` | `string \| null` | Считанные коды |
| `checkerCams[].unread` | `string \| null` | Нечитаемые коды |
| `checkerCams[].state` | `string \| null` | Состояние камеры |
| `checkerCams[].error` | `string \| null` | Признак ошибки |

#### `QUEUE` — очередь заданий

| Поле | Тип | Описание |
|---|---|---|
| `items[].position` | `integer` | Порядковый номер в очереди (1 = следующее) |
| `items[].shortCode` | `string` | Краткий код продукта |
| `items[].batch` | `string` | Номер партии |
| `items[].dateProduced` | `string` | Дата производства партии |

#### `ERRORS` — ошибки устройств и журнал

| Поле | Тип | Описание |
|---|---|---|
| `deviceErrors[].objectName` | `string` | Имя устройства-источника ошибки |
| `deviceErrors[].propertyDesc` | `string` | Название свойства/параметра, у которого значение вышло за норму |
| `deviceErrors[].value` | `string` | Текущее значение параметра (`"1"` = ошибка активна) |
| `deviceErrors[].description` | `string` (опционально) | Дополнительное текстовое описание ошибки |
| `logs[].time` | `string` | Время возникновения события (ISO 8601) |
| `logs[].ackTime` | `string` | Время подтверждения/квитирования события |
| `logs[].group` | `string` | Группа/категория записи журнала |
| `logs[].description` | `string` | Текст записи журнала |

---

## Переменные окружения

| Переменная | Описание |
|---|---|
| `VITE_API_BASE` | Базовый URL REST API (например `http://localhost:8080`) |
| `VITE_WS_BASE` | Базовый URL WebSocket (например `ws://localhost:8080`) |
