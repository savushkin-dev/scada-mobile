import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useListContext, useResourceContext } from 'react-admin';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { PaginationPills } from './PaginationPills';
import { AdminBreadcrumbs } from './AdminBreadcrumbs';
import { EmptyState } from './EmptyState';
import { FilterToolbar } from './FilterToolbar';
import { IconPlus, IconSearch } from './icons';
import { TableFilterProvider, useTableFilters } from '../filters/TableFilterContext';
import type { FilterFieldConfig } from '../filters/types';

interface AdminListContainerProps<T> {
  title: string;
  records: T[];
  /**
   * Декларативное описание фильтруемых колонок. Если передано —
   * над таблицей появляются глобальный поиск, пилюли фильтров и
   * выпадающие фильтры в шапке колонок. Фильтрация выполняется
   * на бэкенде; на фронтенде никакой логики фильтрации данных.
   */
  filterFields?: FilterFieldConfig[];
  filters?: ReactNode;
  /**
   * Переопределение общего числа записей для пагинации.
   * Нужно, когда пагинация выполняется на клиенте поверх уже
   * отфильтрованного списка (например, вкладки уведомлений).
   */
  total?: number;
  /** Показывать ли кнопку "Создать" (по умолчанию — да). */
  showCreate?: boolean;
  children: (props: { records: T[] }) => ReactNode;
}

export function AdminListContainer<T>({
  title,
  records,
  filterFields,
  filters,
  total: totalOverride,
  showCreate = true,
  children,
}: AdminListContainerProps<T>) {
  const { total, page, perPage, setPage, isLoading } = useListContext();

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  const actions = (
    <>
      {filters}
      {showCreate && <CreateButton />}
    </>
  );

  const body = (
    <div className="flex h-full flex-col p-3 lg:px-4 lg:pb-4 lg:pt-4">
      <div className="mb-3 flex flex-col gap-1.5 lg:mb-4">
        <AdminBreadcrumbs />
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <h1 className="text-xl font-bold text-[#1a1c1e]">{title}</h1>
          <div className="hidden lg:flex lg:items-center lg:gap-2">{actions}</div>
          {!filterFields && <div className="flex items-center gap-2 lg:hidden">{actions}</div>}
        </div>
        {filterFields && <FilterToolbar actions={actions} />}
      </div>
      <AdminCard className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
          <ListBody records={records} hasFilters={Boolean(filterFields)}>
            {children}
          </ListBody>
        </div>
        <PaginationPills
          page={page}
          perPage={perPage}
          total={totalOverride ?? total ?? 0}
          onPageChange={setPage}
        />
      </AdminCard>
    </div>
  );

  if (filterFields) {
    return <TableFilterProvider fields={filterFields}>{body}</TableFilterProvider>;
  }
  return body;
}

/**
 * Тело списка. Если фильтры активны и бэкенд вернул пустой результат —
 * показывает понятный плейсхолдер с предложением сбросить фильтры.
 */
function ListBody<T>({
  records,
  hasFilters,
  children,
}: {
  records: T[];
  hasFilters: boolean;
  children: (props: { records: T[] }) => ReactNode;
}) {
  const ctx = useTableFilters();

  if (records.length === 0 && hasFilters && ctx?.hasActiveFilters) {
    return (
      <div className="flex h-full items-center justify-center">
        <EmptyState
          icon={<IconSearch size={36} />}
          title="Ничего не найдено"
          subtitle={
            <>
              Попробуйте изменить фильтры или{' '}
              <button
                type="button"
                onClick={ctx.clearAll}
                className="font-semibold text-[#4285f4] hover:underline"
              >
                очистить все фильтры
              </button>
            </>
          }
        />
      </div>
    );
  }

  return <>{children({ records })}</>;
}

function CreateButton() {
  const resource = useResourceContext();
  const navigate = useNavigate();

  if (!resource) return null;

  // Относительный переход: для списка справочника сохраняет контекст
  // /admin/settings/references/:resource/create, где работают хлебные крошки
  // (useCreatePath вёл бы на канонический /admin/:resource/create вне этого раздела).
  return (
    <PillButton
      icon={<IconPlus size={18} />}
      onClick={() => navigate('create')}
      className="h-9 px-4"
    >
      Создать
    </PillButton>
  );
}
