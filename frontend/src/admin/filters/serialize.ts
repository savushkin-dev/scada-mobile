import type { FieldFilterValue, TableFilterValues } from './types';

/**
 * Слой запросов: преобразует состояние фильтров таблицы в query-параметры
 * единого контракта бэкенда:
 *
 *  - `q` — глобальный поиск;
 *  - `f.<field>=значение` — eq / «содержит» для текста;
 *  - `f.<field>=v1,v2` — OR внутри поля;
 *  - `f.<field>=gt|lt|gte|lte:значение` — сравнение;
 *  - `f.<field>=between:мин,макс` — диапазон.
 */
export function serializeFilterValue(value: FieldFilterValue): string {
  if (Array.isArray(value)) {
    return value.join(',');
  }
  if (typeof value === 'object' && value !== null) {
    if (value.op === 'between') {
      return `between:${value.value},${value.valueTo ?? ''}`;
    }
    return `${value.op}:${value.value}`;
  }
  return value;
}

/** Добавляет параметры фильтрации в URLSearchParams запроса getList. */
export function appendFilterParams(
  query: URLSearchParams,
  filter: TableFilterValues | undefined
): void {
  if (!filter) return;
  const { q, f } = filter;
  if (q && q.trim()) {
    query.set('q', q.trim());
  }
  if (f) {
    Object.entries(f).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      if (Array.isArray(value) && value.length === 0) return;
      query.set(`f.${key}`, serializeFilterValue(value));
    });
  }
}
