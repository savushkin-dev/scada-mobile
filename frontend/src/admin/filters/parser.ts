import type { FilterFieldConfig } from './types';

/**
 * Результат парсинга поисковой строки.
 */
export interface ParsedSearchQuery {
  /** Свободный текст — уходит глобальным поиском (q). */
  globalSearch: string;
  /** Распарсенные структурированные фильтры `ключ:значение`. */
  structured: Record<string, string>;
  /** Токены вида `ключ:значение` с неизвестным ключом или пустым значением — не отправляются на бэкенд. */
  invalidTokens: string[];
}

/**
 * Токен поисковой строки: последовательность сегментов без пробелов между ними.
 * Сегмент — либо текст в двойных кавычках (сохраняет пробелы внутри),
 * либо обычный текст до ближайшего пробела или кавычки.
 */
interface SearchToken {
  /** Исходный текст токена как введён (для отображения и удаления). */
  raw: string;
  /** Раскодированный текст: кавычки сняты, содержимое сегментов склеено. */
  text: string;
}

/**
 * Разбивает строку на токены. Правила:
 *  - токены разделяются пробельными символами;
 *  - `"..."` — один сегмент, пробелы внутри сохраняются;
 *  - незакрытая кавычка поглощает строку до конца (строка парсится
 *    на каждом keystroke, поэтому неполный ввод — штатная ситуация);
 *  - сегменты без пробела между ними склеиваются в один токен
 *    (`ключ:"два слова"` — один токен).
 */
function tokenize(input: string): SearchToken[] {
  const tokens: SearchToken[] = [];
  const length = input.length;
  let i = 0;

  while (i < length) {
    if (/\s/.test(input[i])) {
      i++;
      continue;
    }
    const start = i;
    let text = '';
    while (i < length && !/\s/.test(input[i])) {
      if (input[i] === '"') {
        const close = input.indexOf('"', i + 1);
        if (close === -1) {
          text += input.slice(i + 1);
          i = length;
        } else {
          text += input.slice(i + 1, close);
          i = close + 1;
        }
      } else {
        const start2 = i;
        while (i < length && !/\s/.test(input[i]) && input[i] !== '"') i++;
        text += input.slice(start2, i);
      }
    }
    tokens.push({ raw: input.slice(start, i), text });
  }
  return tokens;
}

/**
 * Строит карту «введённое имя поля (в нижнем регистре) → ключ поля».
 * Поле можно указать ключом API (`fullName`) или, для однословных
 * label, названием колонки (`фио`). При совпадении приоритет у ключа.
 */
function buildFieldLookup(fields: FilterFieldConfig[]): Map<string, string> {
  const lookup = new Map<string, string>();
  for (const field of fields) {
    const labelKey = field.label.toLowerCase();
    if (!lookup.has(labelKey)) {
      lookup.set(labelKey, field.key);
    }
  }
  for (const field of fields) {
    lookup.set(field.key.toLowerCase(), field.key);
  }
  return lookup;
}

/**
 * Чистая функция парсинга поисковой строки.
 *
 * Правила:
 *  - условия разделяются пробелами (логика AND);
 *  - токен с первым двоеточием — `ключ:значение` (структурированный фильтр);
 *  - ключ совпадает с ключом поля или его однословным label (регистронезависимо);
 *  - значение с пробелами берётся в двойные кавычки: `фио:"Иванов Иван"`;
 *  - токен с неизвестным ключом или пустым значением попадает в invalidTokens
 *    и не отправляется;
 *  - остальной текст — глобальный поиск (кавычки снимаются, фраза в кавычках
 *    остаётся цельной);
 *  - значения передаются бэкенду как введены: текст ищется регистронезависимо,
 *    булевы и enum-значения бэкенд приводит к каноническому виду сам.
 *
 * Без скобок, OR и операторов сравнения внутри строки (Фаза 1).
 */
export function parseSearchQuery(input: string, fields: FilterFieldConfig[]): ParsedSearchQuery {
  const structured: Record<string, string> = {};
  const invalidTokens: string[] = [];
  const globalParts: string[] = [];

  const lookup = buildFieldLookup(fields);

  for (const token of tokenize(input)) {
    const colon = token.text.indexOf(':');
    if (colon > 0) {
      const key = lookup.get(token.text.slice(0, colon).toLowerCase());
      const value = token.text.slice(colon + 1);
      if (key && value) {
        structured[key] = value;
      } else {
        invalidTokens.push(token.raw);
      }
    } else if (token.text) {
      globalParts.push(token.text);
    }
  }

  return {
    globalSearch: globalParts.join(' '),
    structured,
    // Дубликаты некорректных токенов схлопываем: пилюля ошибки одна,
    // иначе React получает повторяющиеся key.
    invalidTokens: [...new Set(invalidTokens)],
  };
}

/** Удаляет из строки токен, в точности совпадающий с переданным (например, некорректный). */
export function removeRawToken(input: string, rawToken: string): string {
  return tokenize(input)
    .filter((token) => token.raw !== rawToken)
    .map((token) => token.raw)
    .join(' ')
    .trim();
}

/**
 * Собирает токен `ключ:значение` обратно в строку. Значение с пробельными
 * символами оборачивается в кавычки, чтобы токен пережил повторный парсинг.
 */
export function formatFieldToken(fieldKey: string, value: string): string {
  return /\s/.test(value) ? `${fieldKey}:"${value}"` : `${fieldKey}:${value}`;
}

/**
 * Удаляет токен `ключ:значение` для указанного поля из сырой строки поиска.
 * Поле распознаётся по ключу и однословному label (как при парсинге);
 * для неизвестных полей — точное совпадение ключа токена (удаление
 * некорректных токенов из пилюли ошибки).
 */
export function removeFieldToken(
  input: string,
  fieldKey: string,
  fields: FilterFieldConfig[]
): string {
  const lookup = buildFieldLookup(fields);
  const target = fieldKey.toLowerCase();
  return tokenize(input)
    .filter((token) => {
      const colon = token.text.indexOf(':');
      if (colon <= 0) return true;
      const rawKey = token.text.slice(0, colon).toLowerCase();
      const resolved = lookup.get(rawKey);
      const matches = resolved ? resolved === fieldKey : rawKey === target;
      return !matches;
    })
    .map((token) => token.raw)
    .join(' ')
    .trim();
}
