import { formatEmpty } from './formatEmpty';
import { IconChevronDown, IconChevronUp, IconFilter, IconPowerOff } from './icons';
import { useRef, useState, type ReactNode } from 'react';
import { useListContext } from 'react-admin';
import { useTableFilters } from '../filters/TableFilterContext';
import { ColumnFilterControl } from './ColumnFilterControl';
import { Popover } from './Popover';

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
  /**
   * Имя сортируемого поля сущности (как в backend Pageable).
   * Если задано — клик по заголовку переключает сортировку ASC/DESC;
   * порядок и направление отображаются стрелкой в шапке.
   */
  sortKey?: string;
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
  const { sort, setSort } = useListContext();

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
                  className="whitespace-nowrap pb-3 pt-1 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]"
                >
                  <HeaderCell
                    header={col.header}
                    filterKey={col.filterKey}
                    sortKey={col.sortKey}
                    sort={sort}
                    setSort={setSort}
                  />
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
                  className={`group border-b border-[#f0f0f0] last:border-b-0 even:bg-[#edf0f4] ${
                    active
                      ? ''
                      : 'bg-[#f8f9fa] [&>td]:opacity-60 [&>td:first-child]:opacity-100 [&>td:last-child]:opacity-100'
                  }`}
                >
                  {allColumns.map((col) => (
                    <td
                      key={col.key}
                      className={`py-3 pr-4 transition-colors duration-200 group-hover:bg-[#e2e5e9] first:rounded-l-[12px] last:rounded-r-[12px] ${col.className ?? ''}`}
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

/**
 * Заголовок колонки: кликабельная сортировка (sortKey) + опциональный
 * выпадающий фильтр (filterKey, открывается отдельной кнопкой-иконкой).
 */
function HeaderCell({
  header,
  filterKey,
  sortKey,
  sort,
  setSort,
}: {
  header: ReactNode;
  filterKey?: string;
  sortKey?: string;
  sort: { field: string; order: 'ASC' | 'DESC' };
  setSort: (sort: { field: string; order: 'ASC' | 'DESC' }) => void;
}) {
  const ctx = useTableFilters();
  const [open, setOpen] = useState(false);
  const anchorRef = useRef<HTMLButtonElement>(null);

  const field = filterKey ? ctx?.fields.find((f) => f.key === filterKey) : undefined;

  const isSorted = sortKey != null && sort.field === sortKey;
  const sortActive = isSorted && sortKey !== 'id';
  const handleSortClick = () => {
    if (!sortKey) return;
    setSort({ field: sortKey, order: isSorted && sort.order === 'ASC' ? 'DESC' : 'ASC' });
  };

  if (!ctx || !field) {
    if (!sortKey) return <>{header}</>;
    return (
      <button
        type="button"
        onClick={handleSortClick}
        className="group/header inline-flex cursor-pointer items-center gap-1 uppercase"
      >
        <span>{header}</span>
        {isSorted ? (
          sort.order === 'ASC' ? (
            <IconChevronUp size={14} className="flex-none text-[#4285f4]" />
          ) : (
            <IconChevronDown size={14} className="flex-none text-[#4285f4]" />
          )
        ) : (
          <IconChevronDown
            size={14}
            className="flex-none text-[#e0e2e5] transition-colors group-hover/header:text-[#74777f]"
          />
        )}
      </button>
    );
  }

  const isActive = ctx.filterValues.f?.[field.key] !== undefined;

  return (
    <span className="inline-flex items-center gap-1">
      {sortKey ? (
        <button
          type="button"
          onClick={handleSortClick}
          className={`group/header inline-flex cursor-pointer items-center gap-1 uppercase ${
            sortActive ? 'text-[#4285f4]' : ''
          }`}
        >
          <span>{header}</span>
          {isSorted ? (
            sort.order === 'ASC' ? (
              <IconChevronUp size={14} className="flex-none text-[#4285f4]" />
            ) : (
              <IconChevronDown size={14} className="flex-none text-[#4285f4]" />
            )
          ) : (
            <IconChevronDown
              size={14}
              className="flex-none text-[#e0e2e5] transition-colors group-hover/header:text-[#74777f]"
            />
          )}
        </button>
      ) : (
        <span className="uppercase">{header}</span>
      )}
      <button
        ref={anchorRef}
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={`Фильтр: ${field.label}`}
        aria-expanded={open}
        className="inline-flex cursor-pointer items-center"
      >
        <IconFilter
          size={14}
          className={`flex-none transition-colors ${
            isActive ? 'text-[#4285f4]' : 'text-[#c4c7cc] hover:text-[#74777f]'
          }`}
        />
      </button>
      <Popover open={open} onClose={() => setOpen(false)} anchorRef={anchorRef}>
        <span className="mb-2 block text-xs font-semibold text-[#1a1c1e]">{field.label}</span>
        <ColumnFilterControl field={field} onApplied={() => setOpen(false)} />
      </Popover>
    </span>
  );
}
