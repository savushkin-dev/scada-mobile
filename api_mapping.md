# Сопоставление полей API и MarkPrintServer

В данном документе описано точное соответствие полей JSON, возвращаемых mock-сервером (REST API) для мобильного приложения, и оригинальных свойств объектов в `Br1_MarkServer` (на основе обновленной документации `DATA_FOR_MOBILE.md`).

## 1. `GET /api/workshops` (Список цехов)
Возвращает список доступных цехов на площадке для отображения на главном экране (Дашборд).

| Поле в JSON API | Описание |
|-----------------|----------|
| `id` | Уникальный идентификатор цеха (например, `apparatniy`, `cmp`) |
| `name` | Название цеха (например, `Аппаратный цех`) |
| `totalUnits` | Общее количество аппаратов/линий в цеху |
| `problemUnits` | Количество аппаратов/линий с ошибками или предупреждениями |
| `status` | Общий статус цеха (`Normal`, `Warning`, `Critical`) |

## 2. `GET /api/workshops/{id}/units` (Список аппаратов в цеху)
Возвращает список всех аппаратов/линий для выбранного цеха.

| Поле в JSON API | Описание |
|-----------------|----------|
| `id` | Уникальный идентификатор аппарата/линии |
| `unit` | Название аппарата/линии (например, `Линия розлива ПЭТ №1`) |
| `status` | Текстовый статус (например, `Норма`, `Внимание`, `Критично`) |
| `event` | Текущее событие или описание состояния (например, `Активен розлив`) |
| `timer` | Время простоя или длительность текущего состояния (формат `HH:MM:SS`) |
| `type` | Тип статуса для цветовой индикации (`Normal`, `Warning`, `Critical`) |

## 3. `GET /api/line/status` (Текущая партия и статус линии)
Этот эндпоинт возвращает общую информацию о выбранной линии и текущей активной партии.

| Поле в JSON API | Оригинальное свойство MarkPrintServer | Описание |
|-----------------|---------------------------------------|----------|
| `lineName` | Имя файла проекта (например, `Hassia2.mpr`) | Название линии |
| `lineState` | `Line.ST` | Состояние линии (1 - работа, 0 - стоп) |
| `shortCode` | `BatchQueue.kmc` / `Printer11.kmc` | Краткий код продукта (КМЦ) |
| `kms` | `BatchQueue.kmc` / `Printer11.kmc` | Код производителя (КМЦ) |
| `description` | `Printer11.descr` / `BatchQueue.description` | Описание продукта |
| `ean13` | `Printer11.ean13` / `BatchQueue.ean13` | Штрихкод EAN-13 |
| `batchNumber` | `Printer11.partynumber` / `BatchQueue.batch` | Номер партии |
| `dateProduced` | `Printer11.dateproduced` / `BatchQueue.dateproduced` | Дата выработки |
| `datePacking` | `Printer11.datepack` / `BatchQueue.datepack` | Дата фасовки |
| `dateExpiration` | `Printer11.dateexpiration` / `BatchQueue.dateexpiration` | Дата годности |
| `initialCounter` | `Printer11.curitem` (парсинг первого значения) | Начальный счётчик (кол-во маркировок) |
| `site` | `Printer11.place` / `BatchQueue.place` | Площадка / Место на линии |
| `itf` | `Printer11.itf` / `BatchQueue.itf` | Код ITF-14 |
| `capacity` | `Printer11.emk` / `BatchQueue.emk` | Ёмкость (Количество единиц в коробе) |
| `boxCount` | `Printer11.kole` / `BatchQueue.kole` | Количество коробок / единиц |
| `packageCount` | `Printer11.kolm` / `BatchQueue.kolm` | Количество упаковок / маркировок |
| `freeze` | `BatchQueue.frozen` | Заморозка (1 - заморожено) |
| `region` | `BatchQueue.region` | Код региона |
| `design` | `Printer11.designe` / `BatchQueue.designe` | ID дизайна этикетки |
| `printDM` | `Printer11.printdm` / `BatchQueue.printdm` | Печать DataMatrix (1/0) |

## 4. `GET /api/devices/status` (Статусы устройств и статистика)
Возвращает состояние подключенных устройств (принтеров и камер) и статистику считывания.

| Поле в JSON API | Оригинальное свойство MarkPrintServer | Описание |
|-----------------|---------------------------------------|----------|
| `printer.state` | `Printer11.ST` | Состояние принтера (1 - работа, 0 - стоп) |
| `printer.error` | `scada.LineDev011Error` / `Printer11.Error` | Наличие ошибки на принтере |
| `printer.batch` | `Printer11.curitem` | Текущая позиция на принтере (маркировка \| партия \| дата) |
| `cam41.read` | `scada.Dev041CounterGeneral` / `CamAgregation.BatchSucceeded` | Количество успешно считанных кодов (Камера 41) |
| `cam41.unread` | `scada.Dev041CounterMissing` / `CamAgregation.BatchFailed` | Количество несчитанных/ошибочных кодов (Камера 41) |
| `cam41.state` | `scada.Dev041Work` / `CamAgregation.ST` | Состояние камеры 41 |
| `cam41.error` | `scada.Dev041Error` | Наличие ошибки на камере 41 (агрегированная) |
| `cam41.batch` | `CamAgregation.curitem` | Текущая позиция на камере 41 (счетчик \| партия \| дата) |
| `cam42.read` | `scada.Dev042CounterGeneral` / `CamAgregationBox.BatchSucceeded` | Количество успешно считанных кодов (Камера 42) |
| `cam42.unread` | `scada.Dev042CounterMissing` / `CamAgregationBox.BatchFailed` | Количество несчитанных/ошибочных кодов (Камера 42) |
| `cam42.state` | `scada.Dev042Work` / `CamAgregationBox.ST` | Состояние камеры 42 |
| `cam42.error` | `scada.Dev042Error` | Наличие ошибки на камере 42 (агрегированная) |

## 5. `GET /api/queue` (Очередь печати)
Возвращает список партий, добавленных в очередь на печать.

| Поле в JSON API | Оригинальное свойство MarkPrintServer | Описание |
|-----------------|---------------------------------------|----------|
| `items[].position` | Индекс `BatchQueue.Item01`...`Item10` | Позиция в очереди (1-10) |
| `items[].shortCode` | Парсинг из `BatchQueue.ItemXX` | Краткий код продукта в очереди |
| `items[].batch` | Парсинг из `BatchQueue.ItemXX` | Номер партии в очереди |
| `items[].dateProduced` | Парсинг из `BatchQueue.ItemXX` | Дата выработки для партии в очереди |

## 6. `GET /api/errors` (Ошибки устройств и логи)
Возвращает список активных ошибок оборудования и последние события из журнала.

| Поле в JSON API | Оригинальное свойство MarkPrintServer | Описание |
|-----------------|---------------------------------------|----------|
| `deviceErrors[].objectName` | Имя объекта (например, `Dev041` / `CamAgregation`) | Идентификатор устройства с ошибкой |
| `deviceErrors[].propertyDesc` | Описание флага (например, `scada.Dev041Dublicate`) | Человекочитаемое описание ошибки |
| `deviceErrors[].value` | Значение флага (1 или 0) | Статус ошибки (1 - активна) |
| `logs[].time` | `sqlite_logs.db` (поле времени) | Время возникновения события |
| `logs[].ackTime` | `sqlite_logs.db` (поле подтверждения) | Время подтверждения события оператором |
| `logs[].group` | `sqlite_logs.db` (группа) | Группа события (Система, Ошибка и т.д.) |
| `logs[].description` | `sqlite_logs.db` (описание) | Текст события |
