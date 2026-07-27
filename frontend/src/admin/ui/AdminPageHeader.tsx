import { AdminBreadcrumbs } from './AdminBreadcrumbs';
import type { ReactNode } from 'react';

interface AdminPageHeaderProps {
  title: ReactNode;
  actions?: ReactNode;
}

export function AdminPageHeader({ title, actions }: AdminPageHeaderProps) {
  return (
    <div className="mb-2 flex flex-col gap-0.5 leading-none lg:mb-3">
      <AdminBreadcrumbs />
      <div className="flex items-center justify-between">
        <h1 className="text-lg leading-none font-bold text-[#1a1c1e] lg:text-xl">{title}</h1>
        {actions && <div className="flex items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}
