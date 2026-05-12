# API Reference (SCADA Mobile)

## Purpose
Этот документ фиксирует контракт REST и WebSocket между backend и frontend. Это единый источник правды для формата сообщений и полей.

## Table of contents
- [Purpose](#purpose)
- [Base URL and version](#base-url-and-version)
- [REST endpoints](#rest-endpoints)
- [WebSocket /ws/live](#websocket-wslive)
- [WebSocket /ws/unit/{unitId}](#websocket-wsunitunitid)
- [Client configuration](#client-configuration)

## Base URL and version
REST базовый путь задается в [backend/src/main/resources/application.yaml](backend/src/main/resources/application.yaml#L1-L8) как `/api/${scada.api.version}`. В текущей конфигурации версия `v1.0.0`.

## REST endpoints
### GET /api/v1.0.0/workshops/topology
Назначение: статическая топология цехов. ETag вычисляется из конфигурации в `computeConfigETag` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/WorkshopService.java#L89-L113)), проверка `If-None-Match` выполняется в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L120-L143)).

Схема ответа: `WorkshopTopologyDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/WorkshopTopologyDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/WorkshopTopologyDTO.java#L17-L21)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `id` | string | Идентификатор цеха |
| `name` | string | Отображаемое имя |
| `totalUnits` | int | Количество автоматов в цехе |

HTTP ответы: `200`, `304`.

### GET /api/v1.0.0/workshops/{workshopId}/units/topology
Назначение: статическая топология автоматов цеха. Проверка существования цеха выполняется в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L184-L212)).

Схема ответа: `UnitTopologyDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitTopologyDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitTopologyDTO.java#L13-L17)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `id` | string | Идентификатор автомата |
| `workshopId` | string | Идентификатор цеха |
| `unit` | string | Отображаемое имя автомата |

HTTP ответы: `200`, `304`, `404`.

### GET /api/v1.0.0/workshops/{workshopId}/units/{unitId}/devices/topology
Назначение: статический список устройств PrintSrv автомата. Принадлежность автомата проверяется в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L146-L179)).

Схема ответа: `UnitDeviceTopologyDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitDeviceTopologyDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitDeviceTopologyDTO.java#L17-L21)) с группами устройств `DeviceGroupsDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DeviceGroupsDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DeviceGroupsDTO.java#L21-L26)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `unitId` | string | Идентификатор автомата |
| `workshopId` | string | Идентификатор цеха |
| `unit` | string | Отображаемое имя автомата |
| `devices.printers` | string[] | Принтеры маркировки |
| `devices.aggregationCams` | string[] | Камеры агрегации |
| `devices.aggregationBoxCams` | string[] | Камеры агрегации короба |
| `devices.checkerCams` | string[] | Камеры проверки |

HTTP ответы: `200`, `304`, `404`.

### GET /api/v1.0.0/health/live
Назначение: liveness-проба. Формат ответа задается в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L215-L232)), логика в `HealthService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java#L22-L39)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `status` | string | `UP` или `DOWN` |
| `timestamp` | string | ISO-8601 метка времени |

HTTP ответы: `200`.

### GET /api/v1.0.0/health/ready
Назначение: readiness-проба. Формат ответа задается в контроллере ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/controller/Controller.java#L236-L259)), логика в `HealthService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/HealthService.java#L31-L39)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `status` | string | `UP` или `DOWN` |
| `timestamp` | string | ISO-8601 метка времени |
| `ready` | boolean | Признак готовности |

HTTP ответы: `200`, `503`.

## WebSocket /ws/live
Единый канал live-данных. Обработчик и протокол описаны в `LiveWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/LiveWsHandler.java#L25-L239)).

### Клиентские действия
Клиент отправляет JSON с полем `action`.

| Action | Поля | Назначение |
| --- | --- | --- |
| `SUBSCRIBE_WORKSHOP` | `workshopId` | Подписка на live-статусы автоматов выбранного цеха |
| `UNSUBSCRIBE_WORKSHOP` | отсутствуют | Снятие подписки |

### Серверные сообщения

| Тип | Когда отправляется | Схема |
| --- | --- | --- |
| `ALERT_SNAPSHOT` | при подключении | `AlertSnapshotMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertSnapshotMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertSnapshotMessageDTO.java#L34-L44)) |
| `ALERT` | при дельте алерта | `AlertMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertMessageDTO.java#L39-L47)) |
| `UNITS_STATUS` | при изменении статуса автоматов подписанного цеха | `UnitsStatusMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitsStatusMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitsStatusMessageDTO.java#L23-L34)) |

#### ALERT_SNAPSHOT
| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `ALERT_SNAPSHOT` |
| `payload` | AlertMessage[] | Список активных алертов |

Схема элемента массива: `AlertMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertMessageDTO.java#L39-L47)).

#### ALERT
| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `ALERT` |
| `workshopId` | string | Идентификатор цеха |
| `unitId` | string | Идентификатор автомата |
| `unitName` | string | Отображаемое имя автомата |
| `severity` | string | Текущая реализация использует только `Critical`, см. `AlertService` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/services/AlertService.java#L35-L137)) |
| `active` | boolean | `true` для появления, `false` для снятия |
| `errors` | AlertError[] | Детали ошибок |
| `timestamp` | string | ISO-8601 метка времени |

Схема элемента `errors`: `AlertErrorDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertErrorDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/AlertErrorDTO.java#L10-L10)).

#### UNITS_STATUS
| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `UNITS_STATUS` |
| `workshopId` | string | Идентификатор цеха |
| `payload` | UnitStatus[] | Список статусов автоматов |

Схема элемента массива: `UnitStatusDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitStatusDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/UnitStatusDTO.java#L12-L15)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `unitId` | string | Идентификатор автомата |
| `workshopId` | string | Идентификатор цеха |
| `event` | string | Текущее событие/состояние |

## WebSocket /ws/unit/{unitId}
Канал детальной страницы автомата. Обработчик и протокол описаны в `UnitWsHandler` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L27-L199)). Канал push-only, входящие сообщения игнорируются.

При подключении отправляется начальный пакет из четырех сообщений (`LINE_STATUS`, `DEVICES_STATUS`, `QUEUE`, `ERRORS`), см. `sendInitialSnapshot` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/infrastructure/ws/UnitWsHandler.java#L172-L187)).

### LINE_STATUS
Схема: `LineStatusMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/LineStatusMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/LineStatusMessageDTO.java#L30-L85)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `LINE_STATUS` |
| `unitId` | string | Идентификатор автомата |
| `timestamp` | string | ISO-8601 метка времени |
| `payload.lineName` | string | Название линии |
| `payload.lineState` | string | Состояние линии (`1` или `0`) |
| `payload.shortCode` | string | Краткий код продукта |
| `payload.description` | string | Описание продукта |
| `payload.ean13` | string | EAN-13 |
| `payload.batchNumber` | string | Номер партии |
| `payload.dateProduced` | string | Дата выработки |
| `payload.datePacking` | string | Дата фасовки |
| `payload.dateExpiration` | string | Дата годности |
| `payload.initialCounter` | string | Начальный счетчик |
| `payload.site` | string | Площадка |
| `payload.itf` | string | ITF-14 |
| `payload.capacity` | string | Емкость |
| `payload.boxCount` | string | Количество коробок |
| `payload.packageCount` | string | Количество упаковок |
| `payload.freeze` | string | Признак заморозки |
| `payload.region` | string | Регион |
| `payload.design` | string | Дизайн |
| `payload.printDM` | string | Печать DataMatrix |

### DEVICES_STATUS
Схема: `DevicesStatusMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DevicesStatusMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DevicesStatusMessageDTO.java#L35-L92)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `DEVICES_STATUS` |
| `unitId` | string | Идентификатор автомата |
| `timestamp` | string | ISO-8601 метка времени |
| `payload.printers` | PrinterStatus[] | Статусы принтеров |
| `payload.aggregationCams` | CameraStatus[] | Статусы камер агрегации |
| `payload.aggregationBoxCams` | CameraStatus[] | Статусы камер агрегации короба |
| `payload.checkerCams` | CameraStatus[] | Статусы камер проверки |

PrinterStatus: `DevicesStatusMessageDTO.PrinterStatus` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DevicesStatusMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DevicesStatusMessageDTO.java#L70-L75)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `deviceName` | string | Имя устройства |
| `state` | string | Состояние (`1` или `0`) |
| `error` | string | Код ошибки |
| `batch` | string | Текущая позиция |

CameraStatus: `DevicesStatusMessageDTO.CameraStatus` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DevicesStatusMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/DevicesStatusMessageDTO.java#L86-L91)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `deviceName` | string | Имя устройства |
| `read` | string | Считано |
| `unread` | string | Несчитано |
| `state` | string | Состояние |
| `error` | string | Код ошибки |

### QUEUE
Схема: `QueueMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/QueueMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/QueueMessageDTO.java#L33-L68)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `QUEUE` |
| `unitId` | string | Идентификатор автомата |
| `timestamp` | string | ISO-8601 метка времени |
| `payload.items` | QueueItem[] | Очередь партий |

QueueItem: `QueueMessageDTO.Item` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/QueueMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/QueueMessageDTO.java#L63-L68)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `position` | int | Позиция в очереди |
| `shortCode` | string | Краткий код |
| `batch` | string | Номер партии |
| `dateProduced` | string | Дата выработки |

### ERRORS
Схема: `ErrorsMessageDTO` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/ErrorsMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/ErrorsMessageDTO.java#L36-L66)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `type` | string | Всегда `ERRORS` |
| `unitId` | string | Идентификатор автомата |
| `timestamp` | string | ISO-8601 метка времени |
| `payload.deviceErrors` | DeviceErrorFlag[] | Активные ошибки устройств |
| `payload.logs` | object[] | Журнал событий (массив объектов) |

DeviceErrorFlag: `ErrorsMessageDTO.DeviceErrorFlag` ([backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/ErrorsMessageDTO.java](backend/src/main/java/dev/savushkin/scada/mobile/backend/api/dto/ErrorsMessageDTO.java#L65-L66)).

| Поле | Тип | Описание |
| --- | --- | --- |
| `objectName` | string | Идентификатор устройства |
| `propertyDesc` | string | Ключ свойства |
| `value` | string | Значение флага |
| `description` | string | Описание ошибки |

## Client configuration
Frontend может переопределять базовые URL через переменные окружения. Проверка и дефолты заданы в `runtime.ts` и `EnvSchema`.

| Переменная | Назначение | Источник |
| --- | --- | --- |
| `VITE_API_BASE` | Базовый HTTP URL для REST API | [frontend/src/config/runtime.ts](frontend/src/config/runtime.ts#L14-L22), [frontend/src/schemas/env.ts](frontend/src/schemas/env.ts#L9-L19) |
| `VITE_WS_BASE` | Базовый WS URL для WebSocket | [frontend/src/config/runtime.ts](frontend/src/config/runtime.ts#L14-L22), [frontend/src/schemas/env.ts](frontend/src/schemas/env.ts#L9-L19) |
