import { useNavigate } from 'react-router-dom';
import { IconChevronRight } from './icons';

interface AdminBreadcrumbsProps {
  resource: string;
  resourceLabel: string;
  recordName?: string;
  isCreate?: boolean;
}

export function AdminBreadcrumbs({
  resource,
  resourceLabel,
  recordName,
  isCreate,
}: AdminBreadcrumbsProps) {
  const navigate = useNavigate();

  return (
    <nav className="mb-3 flex items-center gap-1.5 text-sm lg:mb-4">
      <button
        type="button"
        onClick={() => navigate('/admin/' + resource)}
        className="text-[#74777f] transition-colors hover:text-[#1a1c1e]"
      >
        {resourceLabel}
      </button>
      <IconChevronRight size={16} className="text-[#b0b3b8]" />
      <span className="font-medium text-[#1a1c1e]">
        {isCreate ? 'Создать' : recordName || 'Редактирование'}
      </span>
    </nav>
  );
}
