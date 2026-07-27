import { formatEmpty } from './formatEmpty';
import { IconPowerOff } from './icons';
import type { ReactNode } from 'react';

interface Column<T> {
  key: string;
  header: ReactNode;
  render: (record: T) => ReactNode;
  className?: string;
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
                  {col.header}
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
