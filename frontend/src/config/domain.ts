/**
 * Доменные константы UI-слоя.
 *
 * Здесь хранятся только стабильные значения предметной области,
 * которые не зависят от конкретных компонентов.
 *
 * Визуальные/текстовые настройки вынесены в:
 * - {@link ./ui.ts}
 * - {@link ./styles.ts}
 * - {@link ./runtime.ts}
 */
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

export const BOOLEAN_LABEL = Object.freeze({
  yes: 'Да',
  no: 'Нет',
});

export const HTTP_REQUEST = Object.freeze({
  post: 'POST',
  jsonContentType: 'application/json',
});
