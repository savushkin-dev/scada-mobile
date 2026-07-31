/**
 * Декларативное описание фильтруемых колонок таблицы админ-панели.
 *
 * Компонент фильтрации не знает о конкретных сущностях: добавление
 * фильтрации в новую таблицу = передача массива FilterFieldConfig.
 */

export type FilterFieldType = 'text' | 'number' | 'enum' | 'bool' | 'date' | 'search-select';

export interface FilterFieldOption {
  value: string;
  label: string;
}

export interface FilterFieldConfig {
  /** Ключ поля в контракте API (параметр `f.<key>`). */
  key: string;
  /** Человекочитаемый label для пилюль и выпадающих фильтров. */
  label: string;
  type: FilterFieldType;
  /** Статические опции для enum/bool. */
  options?: FilterFieldOption[];
  /** Имя ресурса react-admin для динамических опций enum (например, 'roles'). */
  reference?: string;
  /** Поле записи справочника для подписи опции (по умолчанию 'name'). */
  optionText?: string;
  /** Полная замена текста пилюли (вместо «label: значение»), напр. «Архив». */
  chipLabel?: string;
  /** Значения, для которых пилюля не показывается (например, значение по умолчанию). */
  chipHiddenValues?: string[];
}

/** Операторы сравнения для числовых полей и дат. */
export type FilterOperator = 'eq' | 'gt' | 'lt' | 'gte' | 'lte' | 'between';

/** Значение фильтра с оператором сравнения (number/date). */
export interface ComparisonFilterValue {
  op: FilterOperator;
  value: string;
  /** Вторая граница для оператора between. */
  valueTo?: string;
}

/**
 * Значение структурированного фильтра:
 *  - string — одно значение (eq; для text — «содержит»);
 *  - string[] — несколько значений (OR внутри поля);
 *  - ComparisonFilterValue — сравнение для number/date.
 */
export type FieldFilterValue = string | string[] | ComparisonFilterValue;

/** Состояние фильтрации таблицы (хранится в filterValues react-admin и в URL). */
export interface TableFilterValues {
  /** Глобальный поиск по всем текстовым полям сущности. */
  q?: string;
  /** Структурированные фильтры «поле — значение». */
  f?: Record<string, FieldFilterValue>;
}
