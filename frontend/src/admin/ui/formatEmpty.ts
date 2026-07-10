/**
 * Форматирует отсутствующее значение для отображения в таблицах и карточках.
 * Пустые строки, null и undefined заменяются на длинное тире.
 */
export function formatEmpty<T>(value: T | null | undefined): T | string {
  if (value === null || value === undefined || (typeof value === 'string' && value.trim() === '')) {
    return '—';
  }
  return value;
}
