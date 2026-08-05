import { describe, expect, it } from 'vitest';
import {
  DEVICE_CATALOG_FILTER_FIELDS,
  DEVICE_FILTER_FIELDS,
  DEVICE_TYPE_FILTER_FIELDS,
  NOTIFICATION_FILTER_FIELDS,
  ROLE_FILTER_FIELDS,
  UNIT_FILTER_FIELDS,
  USER_FILTER_FIELDS,
  WORKSHOP_FILTER_FIELDS,
} from './configs';
import { formatFieldToken, parseSearchQuery, removeFieldToken, removeRawToken } from './parser';
import type { FilterFieldConfig } from './types';

/** Все таблицы админ-панели с фильтрацией: [ресурс, конфиг полей]. */
const ALL_TABLES: Array<[string, FilterFieldConfig[]]> = [
  ['roles', ROLE_FILTER_FIELDS],
  ['workshops', WORKSHOP_FILTER_FIELDS],
  ['device-types', DEVICE_TYPE_FILTER_FIELDS],
  ['units', UNIT_FILTER_FIELDS],
  ['device-catalog', DEVICE_CATALOG_FILTER_FIELDS],
  ['users', USER_FILTER_FIELDS],
  ['devices', DEVICE_FILTER_FIELDS],
  ['notifications', NOTIFICATION_FILTER_FIELDS],
];

describe('parseSearchQuery: все поля всех таблиц', () => {
  it.each(ALL_TABLES)('%s: каждое поле доступно по ключу', (_resource, fields) => {
    for (const field of fields) {
      const parsed = parseSearchQuery(`${field.key}:тест`, fields);
      expect(parsed.structured, `поле ${field.key}`).toEqual({ [field.key]: 'тест' });
      expect(parsed.globalSearch).toBe('');
      expect(parsed.invalidTokens).toEqual([]);
    }
  });

  it.each(ALL_TABLES)('%s: каждое поле доступно по однословному label', (_resource, fields) => {
    for (const field of fields) {
      if (/\s/.test(field.label)) continue; // многословные label токеном не вводятся
      const parsed = parseSearchQuery(`${field.label}:тест`, fields);
      expect(parsed.structured, `label ${field.label}`).toEqual({ [field.key]: 'тест' });
      expect(parsed.invalidTokens).toEqual([]);
    }
  });
});

describe('parseSearchQuery: глобальный поиск', () => {
  const fields = USER_FILTER_FIELDS;

  it('пустая строка', () => {
    expect(parseSearchQuery('', fields)).toEqual({
      globalSearch: '',
      structured: {},
      invalidTokens: [],
    });
  });

  it('строка из пробелов', () => {
    expect(parseSearchQuery('   \t \n ', fields).globalSearch).toBe('');
  });

  it('свободный текст уходит в q', () => {
    const parsed = parseSearchQuery('грюнвальд', fields);
    expect(parsed.globalSearch).toBe('грюнвальд');
    expect(parsed.structured).toEqual({});
  });

  it('несколько слов склеиваются пробелом', () => {
    expect(parseSearchQuery('два слова', fields).globalSearch).toBe('два слова');
  });

  it('фраза в кавычках остаётся цельной фразой', () => {
    expect(parseSearchQuery('"два слова" и третье', fields).globalSearch).toBe(
      'два слова и третье'
    );
  });

  it('токен с двоеточием в начале — не фильтр, а текст', () => {
    expect(parseSearchQuery(':2', fields).globalSearch).toBe(':2');
  });
});

describe('parseSearchQuery: структурированные токены', () => {
  const fields = USER_FILTER_FIELDS;

  it('пример из спецификации: id:2', () => {
    expect(parseSearchQuery('id:2', fields).structured).toEqual({ id: '2' });
  });

  it('пример из спецификации: фио:"Логинов Глеб Олегович"', () => {
    const parsed = parseSearchQuery('фио:"Логинов Глеб Олегович"', fields);
    expect(parsed.structured).toEqual({ fullName: 'Логинов Глеб Олегович' });
    expect(parsed.globalSearch).toBe('');
    expect(parsed.invalidTokens).toEqual([]);
  });

  it('несколько условий в одной строке (AND)', () => {
    const parsed = parseSearchQuery('id:2 fullName:иванов активный', fields);
    expect(parsed.structured).toEqual({ id: '2', fullName: 'иванов' });
    expect(parsed.globalSearch).toBe('активный');
  });

  it('регистр ключа не важен: ID:2, Id:2', () => {
    expect(parseSearchQuery('ID:2', fields).structured).toEqual({ id: '2' });
    expect(parseSearchQuery('Id:2', fields).structured).toEqual({ id: '2' });
  });

  it('регистр русского label не важен: ФИО:иванов, Фио:иванов', () => {
    expect(parseSearchQuery('ФИО:иванов', fields).structured).toEqual({ fullName: 'иванов' });
    expect(parseSearchQuery('Фио:иванов', fields).structured).toEqual({ fullName: 'иванов' });
  });

  it('значение передаётся без изменения регистра (бэкенд сам приводит)', () => {
    expect(parseSearchQuery('fullName:ИвАноВ', fields).structured).toEqual({
      fullName: 'ИвАноВ',
    });
    expect(parseSearchQuery('active:TRUE', fields).structured).toEqual({ active: 'TRUE' });
  });

  it('повторный ключ: последнее значение выигрывает', () => {
    expect(parseSearchQuery('id:1 id:2', fields).structured).toEqual({ id: '2' });
  });

  it('ключ API побеждает при совпадении с чужим label', () => {
    // В уведомлениях есть и ключ read, и label «Статус» у того же поля;
    // синтетическая коллизия: ключ совпадает с label другого поля.
    const collision: FilterFieldConfig[] = [
      { key: 'type', label: 'Тип', type: 'text' },
      { key: 'kind', label: 'type', type: 'text' },
    ];
    expect(parseSearchQuery('type:x', collision).structured).toEqual({ type: 'x' });
  });
});

describe('parseSearchQuery: кавычки', () => {
  const fields = NOTIFICATION_FILTER_FIELDS;

  it('значение с пробелами в кавычках — один фильтр', () => {
    const parsed = parseSearchQuery('message:"кончилась плёнка"', fields);
    expect(parsed.structured).toEqual({ message: 'кончилась плёнка' });
    expect(parsed.globalSearch).toBe('');
  });

  it('несколько токенов с кавычками подряд', () => {
    const parsed = parseSearchQuery('instanceId:"цех 1" deviceCode:"printer 11"', fields);
    expect(parsed.structured).toEqual({ instanceId: 'цех 1', deviceCode: 'printer 11' });
  });

  it('незакрытая кавычка — значение до конца строки (ввод на лету)', () => {
    const parsed = parseSearchQuery('message:"кончилась плён', fields);
    expect(parsed.structured).toEqual({ message: 'кончилась плён' });
    expect(parsed.invalidTokens).toEqual([]);
  });

  it('двоеточие внутри кавычек — часть значения', () => {
    expect(parseSearchQuery('message:"ошибка: нет связи"', fields).structured).toEqual({
      message: 'ошибка: нет связи',
    });
  });

  it('пустые кавычки — некорректный токен', () => {
    const parsed = parseSearchQuery('message:""', fields);
    expect(parsed.structured).toEqual({});
    expect(parsed.invalidTokens).toEqual(['message:""']);
  });

  it('текст после закрывающей кавычки склеивается в значение', () => {
    expect(parseSearchQuery('message:"кончилась" плёнка', fields).structured).toEqual({
      message: 'кончилась',
    });
  });
});

describe('parseSearchQuery: пробелы и разделители', () => {
  const fields = ROLE_FILTER_FIELDS;

  it('лишние пробелы между токенами', () => {
    const parsed = parseSearchQuery('   id:2    name:тест   ', fields);
    expect(parsed.structured).toEqual({ id: '2', name: 'тест' });
    expect(parsed.globalSearch).toBe('');
  });

  it('табуляции и переводы строк разделяют токены', () => {
    const parsed = parseSearchQuery('id:2\tname:тест\nтекст', fields);
    expect(parsed.structured).toEqual({ id: '2', name: 'тест' });
    expect(parsed.globalSearch).toBe('текст');
  });
});

describe('parseSearchQuery: некорректные токены', () => {
  const fields = UNIT_FILTER_FIELDS;

  it('несуществующее поле — в invalidTokens, на бэкенд не уходит', () => {
    const parsed = parseSearchQuery('foo:bar id:2', fields);
    expect(parsed.structured).toEqual({ id: '2' });
    expect(parsed.invalidTokens).toEqual(['foo:bar']);
    expect(parsed.globalSearch).toBe('');
  });

  it('несуществующее русское поле', () => {
    expect(parseSearchQuery('фуу:бар', fields).invalidTokens).toEqual(['фуу:бар']);
  });

  it('некорректный токен с кавычками хранится как введён', () => {
    expect(parseSearchQuery('foo:"два слова"', fields).invalidTokens).toEqual(['foo:"два слова"']);
  });

  it('ключ без значения — некорректный токен', () => {
    const parsed = parseSearchQuery('name:', fields);
    expect(parsed.structured).toEqual({});
    expect(parsed.invalidTokens).toEqual(['name:']);
    expect(parsed.globalSearch).toBe('');
  });

  it('дубликаты некорректных токенов схлопываются', () => {
    expect(parseSearchQuery('foo:1 foo:1', fields).invalidTokens).toEqual(['foo:1']);
  });
});

describe('parseSearchQuery: спецсимволы в значениях', () => {
  const fields = NOTIFICATION_FILTER_FIELDS;

  it('двоеточие в значении без кавычек: сплит по первому', () => {
    expect(parseSearchQuery('message:код:123', fields).structured).toEqual({
      message: 'код:123',
    });
  });

  it('апостроф не является кавычкой', () => {
    expect(parseSearchQuery("message:O'Brien", fields).structured).toEqual({
      message: "O'Brien",
    });
  });

  it('запятая передаётся как есть (бэкенд трактует как OR)', () => {
    expect(parseSearchQuery('type:INFO,WARNING', fields).structured).toEqual({
      type: 'INFO,WARNING',
    });
  });

  it('ё и unicode в значениях', () => {
    expect(parseSearchQuery('message:плёнка-«стоп»', fields).structured).toEqual({
      message: 'плёнка-«стоп»',
    });
  });

  it('дата как значение', () => {
    expect(parseSearchQuery('createdAt:2026-08-04', fields).structured).toEqual({
      createdAt: '2026-08-04',
    });
  });
});

describe('removeFieldToken', () => {
  const fields = USER_FILTER_FIELDS;

  it('удаляет токен по ключу', () => {
    expect(removeFieldToken('id:2 fullName:иван', 'id', fields)).toBe('fullName:иван');
  });

  it('удаляет токен независимо от регистра ключа', () => {
    expect(removeFieldToken('ID:2 name:x', 'id', fields)).toBe('name:x');
  });

  it('удаляет токен, введённый через label-алиас', () => {
    expect(removeFieldToken('фио:иван id:2', 'fullName', fields)).toBe('id:2');
  });

  it('удаляет токен со значением в кавычках целиком', () => {
    expect(removeFieldToken('фио:"Логинов Глеб Олегович" id:2', 'fullName', fields)).toBe('id:2');
  });

  it('удаляет токен неизвестного поля по сырому ключу (пилюля ошибки)', () => {
    expect(removeFieldToken('foo:bar id:2', 'foo', fields)).toBe('id:2');
  });

  it('не трогает токены других полей и свободный текст', () => {
    expect(removeFieldToken('id:2 текст name:x', 'fullName', fields)).toBe('id:2 текст name:x');
  });

  it('отсутствующий токен — строка без изменений', () => {
    expect(removeFieldToken('id:2', 'code', fields)).toBe('id:2');
  });
});

describe('removeRawToken', () => {
  it('удаляет точное совпадение токена', () => {
    expect(removeRawToken('foo:bar id:2', 'foo:bar')).toBe('id:2');
  });

  it('удаляет токен с кавычками', () => {
    expect(removeRawToken('foo:"два слова" id:2', 'foo:"два слова"')).toBe('id:2');
  });

  it('удаляет все дубликаты токена', () => {
    expect(removeRawToken('foo:1 foo:1 id:2', 'foo:1')).toBe('id:2');
  });

  it('не совпавшийся токен — строка без изменений', () => {
    expect(removeRawToken('foo:1', 'bar:2')).toBe('foo:1');
  });
});

describe('formatFieldToken', () => {
  it('значение без пробелов — как есть', () => {
    expect(formatFieldToken('id', '2')).toBe('id:2');
  });

  it('значение с пробелами оборачивается в кавычки', () => {
    expect(formatFieldToken('fullName', 'Логинов Глеб')).toBe('fullName:"Логинов Глеб"');
  });

  it('round-trip: токен переживает повторный парсинг', () => {
    const fields = USER_FILTER_FIELDS;
    const token = formatFieldToken('fullName', 'Логинов Глеб Олегович');
    expect(parseSearchQuery(token, fields).structured).toEqual({
      fullName: 'Логинов Глеб Олегович',
    });
  });
});
