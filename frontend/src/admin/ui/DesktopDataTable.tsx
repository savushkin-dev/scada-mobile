import { formatEmpty } from './formatEmpty';
import { IconFilter, IconPowerOff } from './icons';
import { useState, type ReactNode } from 'react';
import { useTableFilters } from '../filters/TableFilterContext';
import type { FilterFieldConfig } from '../filters/types';
import { ColumnFilterControl } from './ColumnFilterControl';

interface Column<T> {
  key: string;
  header: ReactNode;
  render: (record: T) => ReactNode;
  className?: string;
  /**
   * Ключ фильтруемого поля (из filterFields таблицы).
   * Если задан и поле описано в конфигурации — в шапке колонки
   * появляется кнопка вызова выпадающего фильтра.
   */
  filterKey?: string;
}

interface DesktopDataTableProps<T> {
  columns: Column<T>[];
  records: T[];
  keyExtractor: (record: T, index: number) => string | number;
  /** Если передан, строка визуально отображается как неактивная (серая) при false. */
  isActive?: (record: T) => boolean;
}

export function DesktopDataTable<T>({
  columns,
  records,
  keyExtractor,
  isActive,
}: DesktopDataTableProps<T>) {
  const showStatus = isActive != null;

  const statusColumn: Column<T> = {
    key: 'inactive-indicator',
    header: '',
    render: (record) => {
      const active = isActive?.(record) ?? true;
      if (active) return <></>;
      return <IconPowerOff size={16} className="text-[#9aa0a6]" />;
    },
    className: 'w-8 pr-2',
  };

  const allColumns = showStatus ? [statusColumn, ...columns] : columns;

  return (
    <div className="hidden min-h-0 flex-1 flex-col overflow-hidden lg:flex">
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse">
          <thead className="sticky top-0 z-10 bg-white">
            <tr>
              {allColumns.map((col) => (
                <th
                  key={col.key}
                  className="pb-3 pt-1 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]"
                >
                  <HeaderCell header={col.header} filterKey={col.filterKey} />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {records.map((record, index) => {
              const active = isActive?.(record) ?? true;
              return (
                <tr
                  key={keyExtractor(record, index)}
                  className={`group border-b border-[#f0f0f0] last:border-b-0 ${
                    active
                      ? ''
                      : 'bg-[#f8f9fa] [&>td]:opacity-60 [&>td:first-child]:opacity-100 [&>td:last-child]:opacity-100'
                  }`}
                >
                  {allColumns.map((col) => (
                    <td
                      key={col.key}
                      className={`py-3 pr-4 transition-colors duration-200 group-hover:bg-[#fafafa] first:rounded-l-[12px] last:rounded-r-[12px] ${col.className ?? ''}`}
                    >
                      {formatEmpty(col.render(record))}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Заголовок колонки с опциональной кнопкой фильтра. */
function HeaderCell({ header, filterKey }: { header: ReactNode; filterKey?: string }) {
  const ctx = useTableFilters();

  if (!ctx || !filterKey) return <>{header}</>;

  const field = ctx.fields.find((f: FilterFieldConfig) => f.key === filterKey);
  if (!field) return <>{header}</>;

  const isActive = ctx.filterValues.f?.[filterKey] !== undefined;

  return (
    <span className="inline-flex items-center gap-1">
      {header}
      <HeaderFilterButton field={field} isActive={isActive} />
    </span>
  );
}

/** Кнопка-воронка в шапке колонки с выпадающим фильтром. */
function HeaderFilterButton({ field, isActive }: { field: FilterFieldConfig; isActive: boolean }) {
  const [open, setOpen] = useState(false);

  return (
    <span className="relative inline-flex">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={`Фильтр: ${field.label}`}
        className={`rounded-[6px] p-0.5 transition-colors ${
          isActive ? 'text-[#4285f4]' : 'text-[#c4c7cc] hover:text-[#74777f]'
        }`}
      >
        <IconFilter size={14} />
      </button>
      {open && (
        <>
          <button
            type="button"
            aria-label="Закрыть фильтр"
            onClick={() => setOpen(false)}
            className="fixed inset-0 z-20 cursor-default bg-transparent"
          />
          <span className="absolute left-0 top-full z-30 mt-1 block w-64 rounded-[16px] border border-[#e8eaed] bg-white p-3 normal-case shadow-[0_8px_24px_rgba(26,28,30,0.12)]">
            <span className="mb-2 block text-xs font-semibold text-[#1a1c1e]">{field.label}</span>
            <ColumnFilterControl field={field} onApplied={() => setOpen(false)} />
          </span>
        </>
      )}
    </span>
  );
}
