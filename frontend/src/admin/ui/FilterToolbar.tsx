import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useTableFilters } from '../filters/TableFilterContext';
import { removeRawToken } from '../filters/parser';
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
export function FilterToolbar({ actions }: { actions?: ReactNode }) {
  const ctx = useTableFilters();
  const [hintOpen, setHintOpen] = useState(false);
  if (!ctx) return null;

  const { fields, filterValues, rawSearch, invalidTokens, hasActiveFilters } = ctx;
  const f = filterValues.f ?? {};

  const exampleKeys = fields.slice(0, 3).map((field) => `${field.key}:значение`);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative flex-1 lg:hidden">
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
          className={`flex h-8 w-8 flex-none items-center justify-center rounded-full text-sm font-semibold transition-colors lg:hidden ${
            hintOpen ? 'bg-[#1a1c1e] text-white' : 'bg-white text-[#74777f] hover:bg-[#f8f9fa]'
          }`}
        >
          ?
        </button>
        {hasActiveFilters && (
          <div className="flex w-full min-w-0 items-center gap-2 lg:w-auto lg:flex-1">
            <ChipStripScroller>
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
                  onRemove={() => ctx.setRawSearch(removeRawToken(rawSearch, token))}
                />
              ))}
            </ChipStripScroller>
            <button
              type="button"
              onClick={ctx.clearAll}
              className="flex-none rounded-full px-3 py-1 text-xs font-semibold text-[#4285f4] transition-colors hover:bg-[#eef3fe]"
            >
              Очистить всё
            </button>
          </div>
        )}
        {actions && (
          <div className="ml-auto flex flex-wrap items-center gap-2 lg:hidden">{actions}</div>
        )}
      </div>

      {hintOpen && (
        <p className="text-xs text-[#74777f]">
          Примеры:{' '}
          {exampleKeys.map((ex) => (
            <code key={ex} className="rounded bg-[#f1f3f4] px-1 py-0.5">
              {ex}
            </code>
          ))}
          . Несколько условий — через пробел, значение с пробелами — в двойных кавычках. Поле можно
          указать ключом или названием колонки.
        </p>
      )}
    </div>
  );
}

// ── Пилюли ──────────────────────────────────────────────────────────────

/**
 * Горизонтально прокручиваемая полоса пилюль с собственным минималистичным
 * индикатором прокрутки вместо нативного скроллбара (кросс-браузерно он то
 * рисует стрелки, то залезает на контент). Высота полосы фиксирована (h-10,
 * вровень с поисковым инпутом), дорожка индикатора занимает своё место
 * всегда — поэтому появление и исчезновение «ползунка» не сдвигает ни
 * пилюли, ни соседние блоки.
 */
function ChipStripScroller({ children }: { children: ReactNode }) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [thumb, setThumb] = useState<{ left: number; width: number } | null>(null);

  // Без массива зависимостей: состав пилюль меняет scrollWidth без
  // изменения размеров самого контейнера, поэтому пересчитываем после
  // каждого рендера (подписки перевешиваются — это дёшево).
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    // Замер откладываем в rAF: setState синхронно в layout-эффекте
    // при каскаде рендеров (применение фильтров из строки поиска)
    // превращался в Maximum update depth exceeded и ронял страницу.
    let raf = 0;
    const update = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        const { scrollWidth, clientWidth, scrollLeft } = el;
        setThumb((prev) => {
          if (scrollWidth <= clientWidth + 1) return prev === null ? prev : null;
          const width = Math.max(24, (clientWidth / scrollWidth) * clientWidth);
          const left = (scrollLeft / (scrollWidth - clientWidth)) * (clientWidth - width);
          // Без этого сравнения каждый кадр клал бы в стейт новый объект
          // и зацикливал перерисовку.
          return prev && Math.abs(prev.left - left) < 0.5 && Math.abs(prev.width - width) < 0.5
            ? prev
            : { left, width };
        });
      });
    };

    update();
    el.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      cancelAnimationFrame(raf);
      el.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  });

  return (
    <div className="flex h-10 min-w-0 flex-1 flex-col">
      <div
        ref={scrollRef}
        className="chip-strip-scroll flex items-start gap-2 overflow-x-auto overflow-y-hidden pt-1.5"
      >
        {children}
      </div>
      <div className="relative mt-auto h-[3px] flex-none">
        {thumb && (
          <div
            className="absolute top-0 h-full rounded-full bg-[#e3e5e8]"
            style={{ left: thumb.left, width: thumb.width }}
          />
        )}
      </div>
    </div>
  );
}

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
      className={`inline-flex flex-none items-center gap-1 whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium ${colors}`}
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

  // Значения по умолчанию (например, «непрочитанные») пилюлей не засоряют тулбар
  const flatValues = Array.isArray(value) ? value : typeof value === 'string' ? [value] : [];
  if (field?.chipHiddenValues && flatValues.every((v) => field.chipHiddenValues!.includes(v))) {
    return null;
  }

  const label = field?.label ?? fieldKey;
  const display = field ? (
    <FilterValueLabel field={field} value={value} />
  ) : (
    formatPlainValue(value)
  );

  return (
    <Chip
      label={
        field?.chipLabel ?? (
          <>
            {label}: {display}
          </>
        )
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
  // Бэкенд приводит enum/bool-значения к каноническому виду сам, поэтому опции
  // сопоставляем без учёта регистра — иначе пилюля показывала бы сырое значение.
  const mapped = values.map(
    (v) => field.options?.find((o) => o.value.toLowerCase() === v.toLowerCase())?.label ?? v
  );
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
