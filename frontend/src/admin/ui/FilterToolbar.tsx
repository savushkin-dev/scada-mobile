import { useState, type ReactNode } from 'react';
import { useTableFilters } from '../filters/TableFilterContext';
import { removeFieldToken } from '../filters/parser';
import type { ComparisonFilterValue, FieldFilterValue, FilterFieldConfig } from '../filters/types';
import { useNameMap } from './useNameMap';
import { IconSearch, IconX } from './icons';

const OP_SYMBOLS: Record<string, string> = {
  eq: '=',
  gt: '>',
  lt: '<',
  gte: '≥',
  lte: '≤',
};

/**
 * Панель фильтрации таблицы: глобальный поисковый инпут с синтаксисом
 * `ключ:значение`, сворачиваемая подсказка и ряд пилюль активных фильтров.
 * Состояние берёт из TableFilterContext (URL — источник правды).
 */
export function FilterToolbar() {
  const ctx = useTableFilters();
  const [hintOpen, setHintOpen] = useState(false);
  if (!ctx) return null;

  const { fields, filterValues, rawSearch, invalidTokens, hasActiveFilters } = ctx;
  const f = filterValues.f ?? {};

  const exampleKeys = fields.slice(0, 3).map((field) => `${field.key}:значение`);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <div className="relative flex-1 lg:w-80 lg:flex-none">
          <IconSearch
            size={18}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-[#74777f]"
          />
          <input
            type="text"
            value={rawSearch}
            onChange={(e) => ctx.setRawSearch(e.target.value)}
            placeholder="Поиск по всем полям..."
            className="h-10 w-full rounded-[12px] border border-[#e8eaed] bg-white pl-9 pr-4 text-sm text-[#1a1c1e] outline-none transition-all focus:border-[#4285f4] focus:ring-2 focus:ring-[rgba(66,133,244,0.15)]"
          />
        </div>
        <button
          type="button"
          onClick={() => setHintOpen((v) => !v)}
          title="Как искать"
          className={`flex h-8 w-8 flex-none items-center justify-center rounded-full text-sm font-semibold transition-colors ${
            hintOpen ? 'bg-[#1a1c1e] text-white' : 'bg-white text-[#74777f] hover:bg-[#f8f9fa]'
          }`}
        >
          ?
        </button>
      </div>

      {hintOpen && (
        <p className="text-xs text-[#74777f]">
          Примеры:{' '}
          {exampleKeys.map((ex) => (
            <code key={ex} className="rounded bg-[#f1f3f4] px-1 py-0.5">
              {ex}
            </code>
          ))}
          . Несколько условий — через пробел.
        </p>
      )}

      {hasActiveFilters && (
        <div className="flex flex-wrap items-center gap-2">
          {filterValues.q && (
            <Chip label={`Поиск: "${filterValues.q}"`} onRemove={ctx.clearGlobalSearch} />
          )}
          {Object.entries(f).map(([key, value]) => (
            <FieldChip key={key} fieldKey={key} fields={fields} value={value} />
          ))}
          {invalidTokens.map((token) => (
            <Chip
              key={token}
              tone="error"
              label={`Некорректный фильтр: ${token}`}
              onRemove={() => ctx.setRawSearch(removeFieldToken(rawSearch, token.split(':')[0]))}
            />
          ))}
          <button
            type="button"
            onClick={ctx.clearAll}
            className="rounded-full px-3 py-1 text-xs font-semibold text-[#4285f4] transition-colors hover:bg-[#eef3fe]"
          >
            Очистить всё
          </button>
        </div>
      )}
    </div>
  );
}

// ── Пилюли ──────────────────────────────────────────────────────────────

function Chip({
  label,
  onRemove,
  tone = 'default',
}: {
  label: ReactNode;
  onRemove: () => void;
  tone?: 'default' | 'error';
}) {
  const colors = tone === 'error' ? 'bg-[#fce8e6] text-[#c5221f]' : 'bg-[#f1f3f4] text-[#1a1c1e]';
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-medium ${colors}`}
    >
      {label}
      <button
        type="button"
        onClick={onRemove}
        aria-label="Удалить фильтр"
        className="rounded-full p-0.5 transition-colors hover:bg-black/10"
      >
        <IconX size={12} />
      </button>
    </span>
  );
}

/** Пилюля одного структурированного фильтра. */
function FieldChip({
  fieldKey,
  fields,
  value,
}: {
  fieldKey: string;
  fields: FilterFieldConfig[];
  value: FieldFilterValue;
}) {
  const ctx = useTableFilters();
  const field = fields.find((f) => f.key === fieldKey);

  if (!ctx) return null;

  const label = field?.label ?? fieldKey;
  const display = field ? (
    <FilterValueLabel field={field} value={value} />
  ) : (
    formatPlainValue(value)
  );

  return (
    <Chip
      label={
        <>
          {label}: {display}
        </>
      }
      onRemove={() => ctx.removeFieldFilter(fieldKey)}
    />
  );
}

/** Отображение значения фильтра с учётом типа поля и опций. */
function FilterValueLabel({ field, value }: { field: FilterFieldConfig; value: FieldFilterValue }) {
  if (typeof value === 'object' && !Array.isArray(value)) {
    return <>{formatComparison(value)}</>;
  }
  const values = Array.isArray(value) ? value : [value];

  if (field.reference) {
    return <ReferenceValuesLabel field={field} values={values} />;
  }
  const mapped = values.map((v) => field.options?.find((o) => o.value === v)?.label ?? v);
  return <>{mapped.join(', ')}</>;
}

/** Подписи значений enum-фильтра со ссылкой на справочник. */
function ReferenceValuesLabel({ field, values }: { field: FilterFieldConfig; values: string[] }) {
  const getName = useNameMap(field.reference ?? '', field.optionText ?? 'name');
  return <>{values.map((v) => getName(v)).join(', ')}</>;
}

function formatComparison(value: ComparisonFilterValue): string {
  if (value.op === 'between') {
    return `${value.value} .. ${value.valueTo ?? '?'}`;
  }
  return `${OP_SYMBOLS[value.op] ?? value.op} ${value.value}`;
}

function formatPlainValue(value: FieldFilterValue): string {
  if (Array.isArray(value)) return value.join(', ');
  if (typeof value === 'object') return formatComparison(value);
  return value;
}
