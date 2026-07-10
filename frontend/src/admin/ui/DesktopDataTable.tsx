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
    <div className="hidden overflow-x-auto lg:block">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                className="pb-3 text-left text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]"
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
              className="border-b border-[#f0f0f0] last:border-b-0 hover:bg-[#fafafa]"
            >
              {columns.map((col) => (
                <td key={col.key} className={`py-3 pr-4 ${col.className ?? ''}`}>
                  {formatEmpty(col.render(record))}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
