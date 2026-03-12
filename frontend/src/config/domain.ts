export const DOMAIN_FLAGS = Object.freeze({
  inactive: 0,
  active: 1,
});

export const DOMAIN_DEFAULTS = Object.freeze({
  emptyValue: '-',
  zeroCount: 0,
  noDataEvent: 'Нет данных',
  workshopName: 'Цех',
  unitName: 'Устройство',
});

export const UNIT_EVENT = Object.freeze({
  empty: '',
  working: 'В работе',
  noData: DOMAIN_DEFAULTS.noDataEvent,
});

export const BOOLEAN_LABEL = Object.freeze({
  yes: 'Да',
  no: 'Нет',
});

export const HTTP_REQUEST = Object.freeze({
  post: 'POST',
  jsonContentType: 'application/json',
});

export const NORMAL_UNIT_EVENTS = new Set<string>([UNIT_EVENT.empty, UNIT_EVENT.working]);
export const NO_DATA_UNIT_EVENTS = new Set<string>([UNIT_EVENT.noData]);
