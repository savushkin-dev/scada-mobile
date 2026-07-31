import { useMemo, useState } from 'react';
import { useGetList } from 'react-admin';
import { useTableFilters } from '../filters/TableFilterContext';
import { IconCheck, IconSearch } from './icons';
import type {
  ComparisonFilterValue,
  FieldFilterValue,
  FilterFieldConfig,
  FilterFieldOption,
  FilterOperator,
} from '../filters/types';

interface ColumnFilterControlProps {
  field: FilterFieldConfig;
  /** Закрыть попап после применения. */
  onApplied?: () => void;
}

const controlClass =
  'h-9 rounded-[10px] border border-[#e8eaed] bg-white px-3 text-sm text-[#1a1c1e] outline-none transition-all focus:border-[#4285f4] focus:ring-2 focus:ring-[rgba(66,133,244,0.15)]';
const inputClass = `${controlClass} w-full`;
/** Числовой ввод без нативных стрелок-спиннеров — они ломают аккуратный вид попапа. */
const numberInputClass = `${inputClass} [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none`;

const applyButtonClass =
  'h-8 flex-1 rounded-full bg-[#1a1c1e] text-xs font-semibold text-white transition-opacity hover:opacity-85';
const resetButtonClass =
  'h-8 rounded-full px-3 text-xs font-semibold text-[#74777f] transition-colors hover:bg-[#f8f9fa] hover:text-[#1a1c1e]';

/**
 * Содержимое выпадающего фильтра колонки. Тип контрола определяется
 * декларативным описанием поля (text / enum / bool / number / date).
 * Компонент не знает о конкретных сущностях.
 */
export function ColumnFilterControl({ field, onApplied }: ColumnFilterControlProps) {
  const ctx = useTableFilters();
  if (!ctx) return null;
  const current = ctx.filterValues.f?.[field.key];

  switch (field.type) {
    case 'text':
      return (
        <TextControl
          initial={typeof current === 'string' ? current : ''}
          onApply={(v) => {
            if (v) {
              ctx.setFieldFilter(field.key, v);
            } else {
              ctx.removeFieldFilter(field.key);
            }
            onApplied?.();
          }}
          onReset={() => {
            ctx.removeFieldFilter(field.key);
            onApplied?.();
          }}
        />
      );
    case 'enum':
    case 'bool':
      return <EnumControl field={field} current={current} />;
    case 'search-select':
      return <SearchSelectControl field={field} current={current} />;
    case 'number':
      return (
        <NumberControl
          initial={typeof current === 'object' && !Array.isArray(current) ? current : undefined}
          onApply={(v) => {
            if (v) {
              ctx.setFieldFilter(field.key, v);
            } else {
              ctx.removeFieldFilter(field.key);
            }
            onApplied?.();
          }}
          onReset={() => {
            ctx.removeFieldFilter(field.key);
            onApplied?.();
          }}
        />
      );
    case 'date':
      return (
        <DateControl
          initial={typeof current === 'object' && !Array.isArray(current) ? current : undefined}
          onApply={(v) => {
            if (v) {
              ctx.setFieldFilter(field.key, v);
            } else {
              ctx.removeFieldFilter(field.key);
            }
            onApplied?.();
          }}
          onReset={() => {
            ctx.removeFieldFilter(field.key);
            onApplied?.();
          }}
        />
      );
    default:
      return null;
  }
}

// ── Text ────────────────────────────────────────────────────────────────

function TextControl({
  initial,
  onApply,
  onReset,
}: {
  initial: string;
  onApply: (value: string) => void;
  onReset: () => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <div className="flex flex-col gap-2">
      <input
        type="text"
        value={value}
        autoFocus
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && onApply(value.trim())}
        placeholder="Содержит..."
        className={inputClass}
      />
      <div className="flex items-center gap-2">
        <button type="button" onClick={() => onApply(value.trim())} className={applyButtonClass}>
          Применить
        </button>
        <button type="button" onClick={onReset} className={resetButtonClass}>
          Сбросить
        </button>
      </div>
    </div>
  );
}

// ── Enum / Bool ─────────────────────────────────────────────────────────

/** Загрузка опций для enum со ссылкой на справочник react-admin. */
function useReferenceOptions(field: FilterFieldConfig): FilterFieldOption[] {
  const isReference =
    (field.type === 'enum' || field.type === 'search-select') && Boolean(field.reference);
  const { data } = useGetList(
    field.reference ?? 'roles',
    {
      pagination: { page: 1, perPage: 1000 },
      sort: { field: 'id', order: 'ASC' },
    },
    { enabled: isReference }
  );

  return useMemo(() => {
    if (field.options) return field.options;
    if (!isReference || !data) return [];
    const text = field.optionText ?? 'name';
    return data.map((record) => ({
      value: String(record.id),
      label: String((record as Record<string, unknown>)[text] ?? record.id),
    }));
  }, [field.options, field.optionText, isReference, data]);
}

function EnumControl({
  field,
  current,
}: {
  field: FilterFieldConfig;
  current: FieldFilterValue | undefined;
}) {
  const ctx = useTableFilters();
  const options = useReferenceOptions(field);

  const selected: string[] = Array.isArray(current)
    ? current
    : typeof current === 'string' && current
      ? [current]
      : [];

  // Live-применение: таблицы небольшие, дополнительный клик не нужен
  const toggle = (value: string) => {
    if (!ctx) return;
    const next = selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value];
    if (next.length === 0) {
      ctx.removeFieldFilter(field.key);
    } else {
      ctx.setFieldFilter(field.key, next);
    }
  };

  if (options.length === 0) {
    return <p className="text-xs text-[#74777f]">Нет доступных значений</p>;
  }

  return (
    <div className="flex max-h-56 flex-col gap-1 overflow-y-auto">
      {options.map((option) => (
        <label
          key={option.value}
          className="flex cursor-pointer items-center gap-2 rounded-[8px] px-1 py-1 text-sm text-[#1a1c1e] hover:bg-[#f8f9fa]"
        >
          <input
            type="checkbox"
            checked={selected.includes(option.value)}
            onChange={() => toggle(option.value)}
            className="h-4 w-4 accent-[#4285f4]"
          />
          <span className="truncate">{option.label}</span>
        </label>
      ))}
    </div>
  );
}

// ── Search-select ───────────────────────────────────────────────────────

/**
 * Выпадающий список со sticky-поиском (для длинных справочников:
 * автоматы, устройства, роли). Мультивыбор с live-применением;
 * выбранные строки подсвечены и отмечены галочкой.
 */
function SearchSelectControl({
  field,
  current,
}: {
  field: FilterFieldConfig;
  current: FieldFilterValue | undefined;
}) {
  const ctx = useTableFilters();
  const options = useReferenceOptions(field);
  const [query, setQuery] = useState('');

  const selected: string[] = Array.isArray(current)
    ? current
    : typeof current === 'string' && current
      ? [current]
      : [];

  // Live-применение: таблицы небольшие, дополнительный клик не нужен
  const toggle = (value: string) => {
    if (!ctx) return;
    const next = selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value];
    if (next.length === 0) {
      ctx.removeFieldFilter(field.key);
    } else {
      ctx.setFieldFilter(field.key, next);
    }
  };

  const visible = options.filter((o) =>
    o.label.toLowerCase().includes(query.trim().toLowerCase())
  );

  return (
    <div className="flex flex-col gap-2">
      <div className="relative">
        <IconSearch
          size={16}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-[#74777f]"
        />
        <input
          type="text"
          value={query}
          autoFocus
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Поиск..."
          className={`${inputClass} pl-9`}
        />
      </div>
      {visible.length === 0 ? (
        <p className="px-1 py-2 text-xs text-[#74777f]">Ничего не найдено</p>
      ) : (
        <div className="flex max-h-56 flex-col gap-0.5 overflow-y-auto">
          {visible.map((option) => {
            const isSelected = selected.includes(option.value);
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => toggle(option.value)}
                className={`flex items-center justify-between gap-2 rounded-[8px] px-2 py-1.5 text-left text-sm transition-colors ${
                  isSelected
                    ? 'bg-[#e8f0fe] font-medium text-[#1a73e8]'
                    : 'text-[#1a1c1e] hover:bg-[#f8f9fa]'
                }`}
              >
                <span className="truncate">{option.label}</span>
                {isSelected && <IconCheck size={16} className="flex-none" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ── Number ──────────────────────────────────────────────────────────────

const NUMBER_OPERATORS: { value: FilterOperator; label: string }[] = [
  { value: 'eq', label: '=' },
  { value: 'gt', label: '>' },
  { value: 'lt', label: '<' },
  { value: 'gte', label: '≥' },
  { value: 'lte', label: '≤' },
  { value: 'between', label: '..' },
];

function NumberControl({
  initial,
  onApply,
  onReset,
}: {
  initial?: ComparisonFilterValue;
  onApply: (value: ComparisonFilterValue | undefined) => void;
  onReset: () => void;
}) {
  const [op, setOp] = useState<FilterOperator>(initial?.op ?? 'eq');
  const [value, setValue] = useState(initial?.value ?? '');
  const [valueTo, setValueTo] = useState(initial?.valueTo ?? '');

  const apply = () => {
    if (!value.trim()) {
      onApply(undefined);
      return;
    }
    onApply({
      op,
      value: value.trim(),
      valueTo: op === 'between' ? valueTo.trim() : undefined,
    });
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2">
        <select
          value={op}
          onChange={(e) => setOp(e.target.value as FilterOperator)}
          className={`${controlClass} w-14 flex-none px-2`}
        >
          {NUMBER_OPERATORS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <input
          type="number"
          value={value}
          autoFocus
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && apply()}
          placeholder={op === 'between' ? 'от' : 'значение'}
          className={numberInputClass}
        />
      </div>
      {op === 'between' && (
        <input
          type="number"
          value={valueTo}
          onChange={(e) => setValueTo(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && apply()}
          placeholder="до"
          className={numberInputClass}
        />
      )}
      <div className="flex items-center gap-2">
        <button type="button" onClick={apply} className={applyButtonClass}>
          Применить
        </button>
        <button type="button" onClick={onReset} className={resetButtonClass}>
          Сбросить
        </button>
      </div>
    </div>
  );
}

// ── Date ────────────────────────────────────────────────────────────────

function DateControl({
  initial,
  onApply,
  onReset,
}: {
  initial?: ComparisonFilterValue;
  onApply: (value: ComparisonFilterValue | undefined) => void;
  onReset: () => void;
}) {
  const initialFrom =
    initial?.op === 'between' || initial?.op === 'gte' ? (initial?.value ?? '') : '';
  const initialTo =
    initial?.op === 'between'
      ? (initial?.valueTo ?? '')
      : initial?.op === 'lte'
        ? initial.value
        : '';

  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);

  const apply = () => {
    if (from && to) {
      onApply({ op: 'between', value: from, valueTo: to });
    } else if (from) {
      onApply({ op: 'gte', value: from });
    } else if (to) {
      onApply({ op: 'lte', value: to });
    } else {
      onApply(undefined);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <label className="flex items-center gap-2 text-xs text-[#74777f]">
        <span className="w-8">с</span>
        <input
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          className={inputClass}
        />
      </label>
      <label className="flex items-center gap-2 text-xs text-[#74777f]">
        <span className="w-8">по</span>
        <input
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          className={inputClass}
        />
      </label>
      <div className="flex items-center gap-2">
        <button type="button" onClick={apply} className={applyButtonClass}>
          Применить
        </button>
        <button type="button" onClick={onReset} className={resetButtonClass}>
          Сбросить
        </button>
      </div>
    </div>
  );
}
