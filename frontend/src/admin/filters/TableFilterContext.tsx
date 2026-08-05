import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useListContext } from 'react-admin';
import { parseSearchQuery, formatFieldToken, removeFieldToken } from './parser';
import type { FieldFilterValue, FilterFieldConfig, TableFilterValues } from './types';

/** Задержка перед отправкой глобального поиска после остановки печати. */
const SEARCH_DEBOUNCE_MS = 400;

export interface TableFilterContextValue {
  fields: FilterFieldConfig[];
  filterValues: TableFilterValues;
  /** Сырое содержимое поискового инпута (включая токены `ключ:значение`). */
  rawSearch: string;
  /** Токены с неизвестными ключами — показываются как ошибка, на бэкенд не уходят. */
  invalidTokens: string[];
  hasActiveFilters: boolean;
  setRawSearch: (value: string) => void;
  /** Установить/обновить структурированный фильтр поля (из выпадающего фильтра колонки). */
  setFieldFilter: (key: string, value: FieldFilterValue) => void;
  /** Сбросить фильтр поля (кнопка «×» на пилюле или «Сбросить» в попапе). */
  removeFieldFilter: (key: string) => void;
  /** Убрать глобальный поиск (пилюля «Поиск: "..."»), сохранив токены в инпуте. */
  clearGlobalSearch: () => void;
  clearAll: () => void;
}

const TableFilterContext = createContext<TableFilterContextValue | null>(null);

/**
 * Слой состояния фильтрации таблицы.
 *
 * Единственный источник правды — query-параметры URL: состояние хранится
 * в filterValues react-admin ({ q, f }), который синхронизируется с
 * адресной строкой. Хук не знает о конкретных полях сущности — только
 * о декларативном описании колонок.
 */
export function TableFilterProvider({
  fields,
  children,
}: {
  fields: FilterFieldConfig[];
  children: ReactNode;
}) {
  const { filterValues, setFilters } = useListContext();
  const typedFilterValues = filterValues as TableFilterValues;

  const [rawSearch, setRawSearch] = useState<string>(typedFilterValues.q ?? '');
  const [invalidTokens, setInvalidTokens] = useState<string[]>([]);

  // Актуальные filterValues для использования внутри debounce-таймера
  const filterValuesRef = useRef<TableFilterValues>(typedFilterValues);
  useEffect(() => {
    filterValuesRef.current = typedFilterValues;
  }, [typedFilterValues]);

  // Ключи, которыми сейчас управляют токены поисковой строки
  const tokenKeysRef = useRef<string[]>([]);
  // Подпись последнего применённого состояния — защита от лишних setFilters
  const lastAppliedRef = useRef<string>('');
  const firstDebounceRef = useRef(true);

  // Debounce парсинга поисковой строки
  useEffect(() => {
    const timer = setTimeout(() => {
      const parsed = parseSearchQuery(rawSearch, fields);
      setInvalidTokens(parsed.invalidTokens);

      const prev = filterValuesRef.current;
      const parsedKeys = Object.keys(parsed.structured);

      const nextF: Record<string, FieldFilterValue> = { ...(prev.f ?? {}) };
      // убрать фильтры, ранее заданные токенами, которых больше нет в строке
      for (const key of tokenKeysRef.current) {
        if (!parsedKeys.includes(key)) {
          delete nextF[key];
        }
      }
      tokenKeysRef.current = parsedKeys;
      for (const key of parsedKeys) {
        nextF[key] = parsed.structured[key];
      }

      const next: TableFilterValues = {
        q: parsed.globalSearch || undefined,
        f: Object.keys(nextF).length > 0 ? nextF : undefined,
      };
      const signature = JSON.stringify(next);
      // При первом запуске не дёргаем setFilters, если состояние уже совпадает
      // (например, восстановлено из URL).
      if (firstDebounceRef.current) {
        firstDebounceRef.current = false;
        const current = JSON.stringify({ q: prev.q, f: prev.f });
        lastAppliedRef.current = signature;
        if (current === signature) return;
      }
      if (signature === lastAppliedRef.current) return;
      lastAppliedRef.current = signature;
      setFilters(next);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [rawSearch, fields, setFilters]);

  const setFieldFilter = useCallback(
    (key: string, value: FieldFilterValue) => {
      // Фильтр из шапки колонки выигрывает у токена в строке поиска
      setRawSearch((prev) => removeFieldToken(prev, key, fields));
      tokenKeysRef.current = tokenKeysRef.current.filter((k) => k !== key);
      const prev = filterValuesRef.current;
      const next: TableFilterValues = {
        q: prev.q,
        f: { ...(prev.f ?? {}), [key]: value },
      };
      lastAppliedRef.current = JSON.stringify(next);
      setFilters(next);
    },
    [fields, setFilters]
  );

  const removeFieldFilter = useCallback(
    (key: string) => {
      setRawSearch((prev) => removeFieldToken(prev, key, fields));
      tokenKeysRef.current = tokenKeysRef.current.filter((k) => k !== key);
      const prev = filterValuesRef.current;
      const nextF = { ...(prev.f ?? {}) };
      delete nextF[key];
      const next: TableFilterValues = {
        q: prev.q,
        f: Object.keys(nextF).length > 0 ? nextF : undefined,
      };
      lastAppliedRef.current = JSON.stringify(next);
      setFilters(next);
    },
    [fields, setFilters]
  );

  const clearGlobalSearch = useCallback(() => {
    // Оставляем в инпуте только токены `ключ:значение` (валидные и нет)
    setRawSearch((prev) => {
      const parsed = parseSearchQuery(prev, fields);
      const tokens = [
        ...Object.entries(parsed.structured).map(([k, v]) => formatFieldToken(k, v)),
        ...parsed.invalidTokens,
      ];
      return tokens.join(' ');
    });
  }, [fields]);

  const clearAll = useCallback(() => {
    setRawSearch('');
    tokenKeysRef.current = [];
    lastAppliedRef.current = JSON.stringify({});
    setFilters({});
  }, [setFilters]);

  const value = useMemo<TableFilterContextValue>(() => {
    const f = typedFilterValues.f ?? {};
    // Значения, скрытые из пилюль как дефолтные (chipHiddenValues),
    // активными фильтрами не считаются — иначе под тулбаром висит
    // пустой ряд с «Очистить всё».
    const isHiddenDefault = (key: string, v: FieldFilterValue): boolean => {
      const field = fields.find((fld) => fld.key === key);
      if (!field?.chipHiddenValues) return false;
      const flat = Array.isArray(v) ? v : typeof v === 'string' ? [v] : [];
      return flat.length > 0 && flat.every((x) => field.chipHiddenValues!.includes(x));
    };
    const hasActiveFilters =
      Boolean(typedFilterValues.q) ||
      Object.entries(f).some(([key, v]) => !isHiddenDefault(key, v)) ||
      invalidTokens.length > 0;
    return {
      fields,
      filterValues: typedFilterValues,
      rawSearch,
      invalidTokens,
      hasActiveFilters,
      setRawSearch,
      setFieldFilter,
      removeFieldFilter,
      clearGlobalSearch,
      clearAll,
    };
  }, [
    fields,
    typedFilterValues,
    rawSearch,
    invalidTokens,
    setFieldFilter,
    removeFieldFilter,
    clearGlobalSearch,
    clearAll,
  ]);

  return <TableFilterContext.Provider value={value}>{children}</TableFilterContext.Provider>;
}

/** Доступ к состоянию фильтров таблицы. null — если таблица без фильтрации. */
// eslint-disable-next-line react-refresh/only-export-components
export function useTableFilters(): TableFilterContextValue | null {
  return useContext(TableFilterContext);
}
