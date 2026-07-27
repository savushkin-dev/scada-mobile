import { formatEmpty } from './formatEmpty';
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
}

export function DesktopDataTable<T>({ columns, records, keyExtractor }: DesktopDataTableProps<T>) {
  return (
    <div className="hidden min-h-0 flex-1 flex-col overflow-hidden lg:flex">
      <div className="-mr-2 flex-1 overflow-auto pr-2">
        <table className="w-full border-collapse">
          <thead className="sticky top-0 z-10 bg-white">
            <tr>
              {columns.map((col) => (
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
            {records.map((record, index) => (
              <tr
                key={keyExtractor(record, index)}
                className="group border-b border-[#f0f0f0] last:border-b-0"
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={`py-3 pr-4 transition-colors duration-200 group-hover:bg-[#fafafa] first:rounded-l-[12px] last:rounded-r-[12px] ${col.className ?? ''}`}
                  >
                    {formatEmpty(col.render(record))}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
