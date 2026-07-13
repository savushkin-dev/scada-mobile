import { useNavigate } from 'react-router-dom';
import { useListContext, useResourceContext, useCreatePath } from 'react-admin';
import type { ReactNode } from 'react';
import { AdminCard } from './AdminCard';
import { PillButton } from './PillButton';
import { PaginationPills } from './PaginationPills';
import { IconPlus } from './icons';

interface AdminListContainerProps {
  title: string;
  children: ReactNode;
}

export function AdminListContainer({ title, children }: AdminListContainerProps) {
  const { total, page, perPage, setPage, isLoading } = useListContext();

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6">
      <div className="mb-4 flex items-center justify-between lg:mb-6">
        <h1 className="text-xl font-bold text-[#1a1c1e]">{title}</h1>
        <CreateButton />
      </div>
      <AdminCard>
        {children}
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
