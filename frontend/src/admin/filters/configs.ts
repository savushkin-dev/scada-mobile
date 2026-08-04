import type { FilterFieldConfig, FilterFieldOption } from './types';

/**
 * Конфигурации фильтруемых колонок по ресурсам админ-панели.
 * Ключи и типы соответствуют реестру фильтрации на бэкенде
 * (AdminFilterSupport). Добавление фильтрации в новую таблицу =
 * новая запись здесь + `filterFields` в AdminListContainer.
 */

const ACTIVE_OPTIONS: FilterFieldOption[] = [
  { value: 'true', label: 'Активен' },
  { value: 'false', label: 'Неактивен' },
];

export const ROLE_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'name', label: 'Название', type: 'text' },
];

export const WORKSHOP_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'name', label: 'Название', type: 'text' },
  { key: 'active', label: 'Статус', type: 'bool', options: ACTIVE_OPTIONS },
];

export const DEVICE_TYPE_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'code', label: 'Код', type: 'text' },
  { key: 'name', label: 'Название', type: 'text' },
];

export const UNIT_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'name', label: 'Название', type: 'text' },
  { key: 'workshopId', label: 'Цех', type: 'enum', reference: 'workshops', optionText: 'name' },
  { key: 'printsrvInstanceId', label: 'PrintSrv ID', type: 'text' },
  { key: 'printsrvHost', label: 'PrintSrv хост', type: 'text' },
  { key: 'printsrvPort', label: 'PrintSrv порт', type: 'number' },
  {
    key: 'deviceCatalogId',
    label: 'Устройства',
    type: 'search-select',
    reference: 'device-catalog',
    optionText: 'name',
  },
  { key: 'active', label: 'Статус', type: 'bool', options: ACTIVE_OPTIONS },
];

export const DEVICE_CATALOG_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'code', label: 'Код', type: 'text' },
  { key: 'name', label: 'Название', type: 'text' },
  {
    key: 'typeId',
    label: 'Тип',
    type: 'enum',
    reference: 'device-types',
    optionText: 'name',
  },
  { key: 'active', label: 'Статус', type: 'bool', options: ACTIVE_OPTIONS },
];

export const USER_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'code', label: 'Код', type: 'text' },
  { key: 'fullName', label: 'ФИО', type: 'text' },
  { key: 'roleId', label: 'Роль', type: 'search-select', reference: 'roles', optionText: 'name' },
  {
    key: 'unitId',
    label: 'Автоматы',
    type: 'search-select',
    reference: 'units',
    optionText: 'name',
  },
  {
    key: 'incidentUnitId',
    label: 'Тех. сбои',
    type: 'search-select',
    reference: 'units',
    optionText: 'name',
  },
  {
    key: 'callUnitId',
    label: 'Вызов',
    type: 'search-select',
    reference: 'units',
    optionText: 'name',
  },
  { key: 'active', label: 'Статус', type: 'bool', options: ACTIVE_OPTIONS },
];

export const DEVICE_FILTER_FIELDS: FilterFieldConfig[] = [
  { key: 'id', label: 'ID', type: 'number' },
  { key: 'code', label: 'Код', type: 'text' },
  { key: 'displayName', label: 'Отображаемое имя', type: 'text' },
  { key: 'unitId', label: 'Автомат', type: 'enum', reference: 'units', optionText: 'name' },
  {
    key: 'typeId',
    label: 'Тип',
    type: 'enum',
    reference: 'device-types',
    optionText: 'name',
  },
];

export const NOTIFICATION_FILTER_FIELDS: FilterFieldConfig[] = [
  {
    key: 'type',
    label: 'Тип',
    type: 'enum',
    options: [
      { value: 'DEVICE_DISCOVERED', label: 'Новое устройство' },
      { value: 'DEVICE_DISCONNECTED', label: 'Устройство отключено' },
      { value: 'DEVICE_RECONNECTED', label: 'Устройство подключено' },
      { value: 'PASSWORD_CHANGED', label: 'Смена пароля' },
      { value: 'USER_INACTIVE', label: 'Бездействие пользователя' },
    ],
  },
  {
    key: 'severity',
    label: 'Важность',
    type: 'enum',
    options: [
      { value: 'INFO', label: 'INFO' },
      { value: 'WARNING', label: 'WARNING' },
    ],
  },
  { key: 'instanceId', label: 'Автомат', type: 'text' },
  { key: 'deviceCode', label: 'Устройство', type: 'text' },
  { key: 'message', label: 'Сообщение', type: 'text' },
  {
    key: 'read',
    label: 'Статус',
    type: 'bool',
    options: [
      { value: 'false', label: 'Непрочитано' },
      { value: 'true', label: 'Прочитано' },
    ],
    // Непрочитанные — вид по умолчанию, пилюля не нужна; прочитанные — пилюля «Архив»
    chipLabel: 'Архив',
    chipHiddenValues: ['false'],
  },
  { key: 'createdAt', label: 'Время', type: 'date' },
];
