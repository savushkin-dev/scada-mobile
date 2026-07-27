import { useState, useMemo, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useListContext, useResourceContext, useCreatePath } from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { PaginationPills } from './PaginationPills';
import { IconPlus, IconSearch } from './icons';

interface AdminListContainerProps<T> {
  title: string;
  records: T[];
  searchableFields?: (keyof T)[];
  filters?: ReactNode;
  children: (props: { records: T[] }) => ReactNode;
}

export function AdminListContainer<T>({
  title,
  records,
  searchableFields,
  filters,
  children,
}: AdminListContainerProps<T>) {
  const [search, setSearch] = useState('');
  const { total, page, perPage, setPage, isLoading } = useListContext();

  const filteredRecords = useMemo(() => {
    if (!search.trim() || !searchableFields || searchableFields.length === 0) return records;
    const query = search.toLowerCase().trim();
    return records.filter((record) =>
      searchableFields.some((field) => {
        const value = (record as Record<string, unknown>)[field as string];
        if (value == null) return false;
        return String(value).toLowerCase().includes(query);
      })
    );
  }, [records, search, searchableFields]);

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col p-4 lg:p-6">
      <div className="mb-4 flex flex-col gap-3 lg:mb-6 lg:flex-row lg:items-center lg:justify-between">
        <h1 className="text-xl font-bold text-[#1a1c1e]">{title}</h1>
        <div className="flex items-center gap-2">
          {searchableFields && searchableFields.length > 0 && (
            <div className="relative flex-1 lg:w-64">
              <IconSearch
                size={18}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-[#74777f]"
              />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Поиск..."
                className="h-10 w-full rounded-[12px] border border-[#e8eaed] bg-white pl-9 pr-4 text-sm text-[#1a1c1e] outline-none transition-all focus:border-[#4285f4] focus:ring-2 focus:ring-[rgba(66,133,244,0.15)]"
              />
            </div>
          )}
          {filters}
          <CreateButton />
        </div>
      </div>
      <AdminCard className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
          {children({ records: filteredRecords })}
        </div>
        <PaginationPills page={page} perPage={perPage} total={total ?? 0} onPageChange={setPage} />
      </AdminCard>
    </div>
  );
}

function CreateButton() {
  const resource = useResourceContext();
  const getCreatePath = useCreatePath();
  const navigate = useNavigate();

  if (!resource) return null;

  const createPath = getCreatePath({ resource, type: 'create' });

  return (
    <PillButton icon={<IconPlus size={18} />} onClick={() => navigate(createPath)}>
      Создать
    </PillButton>
  );
}
