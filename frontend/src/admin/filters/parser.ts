import type { FilterFieldConfig } from './types';

/**
 * Результат парсинга поисковой строки.
 */
export interface ParsedSearchQuery {
  /** Свободный текст — уходит глобальным поиском (q). */
  globalSearch: string;
  /** Распарсенные структурированные фильтры `ключ:значение`. */
  structured: Record<string, string>;
  /** Токены вида `ключ:значение` с неизвестным ключом — не отправляются на бэкенд. */
  invalidTokens: string[];
}

/**
 * Чистая функция парсинга поисковой строки.
 *
 * Правила:
 *  - условия разделяются пробелами (логика AND);
 *  - токен с первым двоеточием — `ключ:значение` (структурированный фильтр);
 *  - ключ должен совпадать с одним из описанных полей (регистронезависимо);
 *  - токен с неизвестным ключом попадает в invalidTokens и не отправляется;
 *  - остальной текст — глобальный поиск.
 *
 * Без скобок, OR и операторов сравнения внутри строки (Фаза 1).
 */
export function parseSearchQuery(input: string, fields: FilterFieldConfig[]): ParsedSearchQuery {
  const structured: Record<string, string> = {};
  const invalidTokens: string[] = [];
  const globalParts: string[] = [];

  const byKey = new Map(fields.map((f) => [f.key.toLowerCase(), f.key]));

  for (const token of input.split(/\s+/).filter(Boolean)) {
    const colon = token.indexOf(':');
    if (colon > 0 && colon < token.length - 1) {
      const rawKey = token.slice(0, colon);
      const value = token.slice(colon + 1);
      const key = byKey.get(rawKey.toLowerCase());
      if (key) {
        structured[key] = value;
      } else {
        invalidTokens.push(token);
      }
    } else {
      globalParts.push(token);
    }
  }

  return {
    globalSearch: globalParts.join(' '),
    structured,
    invalidTokens,
  };
}

/** Удаляет токен `ключ:значение` для указанного поля из сырой строки поиска. */
export function removeFieldToken(input: string, fieldKey: string): string {
  const prefix = `${fieldKey.toLowerCase()}:`;
  return input
    .split(/\s+/)
    .filter((token) => !token.toLowerCase().startsWith(prefix))
    .join(' ')
    .trim();
}
