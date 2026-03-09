import type { DevicesStatusPayload, LineStatusPayload, TabId } from '../types';

type DeviceKey = keyof DevicesStatusPayload;
type DeviceStatKey = 'read' | 'unread';
type BatchFieldKey = keyof LineStatusPayload;

export interface NavItemConfig {
  tab: TabId;
  icon: string;
  label: string;
}

export interface DeviceStatConfig {
  key: DeviceStatKey;
  label: string;
  danger?: boolean;
}

export interface DeviceSectionConfig {
  key: DeviceKey;
  title: string;
  showBatch?: boolean;
  stats?: readonly DeviceStatConfig[];
}

export interface BatchFieldConfig {
  key: BatchFieldKey;
  label: string;
  format?: 'boolean';
}

export const APP_BRAND = Object.freeze({
  title: 'Савушкин продукт',
  subtitle: 'Площадка г. Брест',
});

export const UI_COPY = Object.freeze({
  noData: 'Нет данных',
  backButtonAriaLabel: 'Назад',
  retryAction: 'Повторить',
  workshopSubtitle: 'Цех',
  workshopTotalUnitsLabel: 'Аппаратов/Линий',
  workshopProblemUnitsLabel: 'Проблемных',
  workshopProblemsBadge: 'Есть проблемы',
  queueTitle: '📋 Очередь печати',
  queueEmpty: 'Очередь пуста',
  queueBatchPrefix: 'Партия',
  queueShortCodePrefix: 'Кр. код',
  queueProducedPrefix: 'Выработка',
  batchTitle: '📦 Текущая партия',
  batchShowMore: 'Показать все свойства ▾',
  batchShowLess: 'Скрыть дополнительные свойства ▴',
  currentBatchLabel: 'Текущая партия',
  statusError: 'Ошибка',
  statusWorking: 'В работе',
  statusStopped: 'Остановлен',
  activeErrorsTitle: '⚠️ Активные ошибки',
  activeErrorsEmpty: '✅ Нет активных ошибок',
  eventLogTitle: '📝 Журнал событий',
  eventLogEmpty: 'Журнал пуст',
  errorFallbackTitle: 'Что-то пошло не так',
  errorFallbackReload: 'Перезагрузить',
  fabActionLabel: 'Последняя партия',
  fabSentLabel: 'Отправлено!',
  fabAriaLabel: 'Сообщить: партия последняя',
  fabDefaultIcon: '🔔',
  fabSentIcon: '✅',
  backIcon: '←',
  retryIcon: '⚠',
});

export const UI_PALETTE = Object.freeze({
  brandText: '#1A1C1E',
  mutedText: '#74777F',
  success: '#34A853',
  critical: '#EA4335',
  retryBackground: '#FFF0F0',
  retryBorder: '#FFCDD2',
  retryText: '#B71C1C',
  softBlue: '#F0F7FF',
  neutralSurface: '#EDEEF0',
  neutralText: '#5F6368',
  fabIdle: '#F97316',
  white: '#FFFFFF',
});

export const DETAIL_TABS = Object.freeze({
  batch: 'tab-batch' as TabId,
  devices: 'tab-devices' as TabId,
  queue: 'tab-queue' as TabId,
  logs: 'tab-logs' as TabId,
});

export const DEFAULT_DETAIL_TAB: TabId = DETAIL_TABS.batch;

export const BOTTOM_NAV_ITEMS: ReadonlyArray<NavItemConfig> = [
  { tab: DETAIL_TABS.batch, icon: '📦', label: 'Партия' },
  { tab: DETAIL_TABS.devices, icon: '⚙️', label: 'Устройства' },
  { tab: DETAIL_TABS.queue, icon: '📋', label: 'Очередь' },
  { tab: DETAIL_TABS.logs, icon: '⚠️', label: 'Журнал' },
];

export const VALID_DETAIL_TABS = new Set<TabId>(BOTTOM_NAV_ITEMS.map(({ tab }) => tab));

export const DEVICE_SECTION_CONFIG: ReadonlyArray<DeviceSectionConfig> = [
  {
    key: 'printer',
    title: '🖨️ Принтер 1',
    showBatch: true,
  },
  {
    key: 'cam41',
    title: '📷 Камера 41 (Поток)',
    showBatch: true,
    stats: [
      { key: 'read', label: 'Считано' },
      { key: 'unread', label: 'Несчитано', danger: true },
    ],
  },
  {
    key: 'cam42',
    title: '📷 Камера 42 (Поток)',
    stats: [
      { key: 'read', label: 'Считано' },
      { key: 'unread', label: 'Несчитано', danger: true },
    ],
  },
];

export const BATCH_PRIMARY_FIELDS: ReadonlyArray<BatchFieldConfig> = [
  { key: 'description', label: 'Описание' },
  { key: 'ean13', label: 'EAN' },
  { key: 'batchNumber', label: 'Номер партии' },
  { key: 'dateProduced', label: 'Дата выработки' },
  { key: 'dateExpiration', label: 'Дата годности' },
];

export const BATCH_ADDITIONAL_FIELDS: ReadonlyArray<BatchFieldConfig> = [
  { key: 'shortCode', label: 'Краткий код' },
  { key: 'kms', label: 'КМС' },
  { key: 'datePacking', label: 'Дата фасовки' },
  { key: 'initialCounter', label: 'Начальный счётчик' },
  { key: 'site', label: 'Площадка' },
  { key: 'itf', label: 'ITF' },
  { key: 'capacity', label: 'Ёмкость' },
  { key: 'boxCount', label: 'Кол-во коробок' },
  { key: 'packageCount', label: 'Кол-во упаковок' },
  { key: 'freeze', label: 'Заморозка', format: 'boolean' },
  { key: 'region', label: 'Регион' },
  { key: 'design', label: 'Дизайн' },
  { key: 'printDM', label: 'Печать DM', format: 'boolean' },
];

export const ERROR_MESSAGES = Object.freeze({
  timeout: 'Запрос был отменён',
  networkUnavailable: 'Нет связи с сервером',
  parseError: 'Получен некорректный ответ от сервера',
  renderCrash: 'Произошла ошибка отображения',
  accessDenied: 'Нет доступа к данным',
  notFound: 'Данные не найдены на сервере',
  serverError: 'Ошибка сервера. Повторите попытку',
  unexpectedError: 'Произошла непредвиденная ошибка',
  unknownError: 'Неизвестная ошибка',
  requestError: (status: number) => `Ошибка запроса (${status})`,
  unexpectedServerResponse: (status: number) => `Неожиданный ответ сервера (${status})`,
});
