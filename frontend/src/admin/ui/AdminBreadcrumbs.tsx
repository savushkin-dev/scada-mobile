import { useLocation, useNavigate } from 'react-router-dom';
import { IconChevronRight } from './icons';

const REFERENCE_LABELS: Record<string, string> = {
  roles: 'Роли',
  workshops: 'Цеха',
  'device-types': 'Типы устройств',
  'device-catalog': 'Справочник устройств',
};

const OPERATIONAL_BACK_LABELS: Record<string, string> = {
  users: 'Назад к списку сотрудников',
  units: 'Назад к списку автоматов',
};

/**
 * Хлебные крошки для админ-панели.
 * На корневых страницах разделов не отображается.
 */
export function AdminBreadcrumbs() {
  const location = useLocation();
  const navigate = useNavigate();
  const pathname = location.pathname;

  // Корневые страницы разделов — без хлебных крошек
  if (
    pathname === '/admin' ||
    pathname === '/admin/' ||
    pathname === '/admin/users' ||
    pathname === '/admin/units' ||
    pathname === '/admin/notifications' ||
    pathname === '/admin/settings'
  ) {
    return null;
  }

  // Справочники: /admin/settings/references[/:resource[/:id|create]]
  const referenceMatch = pathname.match(
    /^\/admin\/settings\/references(?:\/([^/]+))?(?:\/([^/]+))?$/
  );
  if (referenceMatch) {
    const resource = referenceMatch[1];
    const tail = referenceMatch[2];
    const resourceLabel = resource ? (REFERENCE_LABELS[resource] ?? resource) : null;

    return (
      <nav className="flex items-center gap-1.5 text-xs leading-none">
        <button
          type="button"
          onClick={() => navigate('/admin/settings')}
          className="leading-none text-[#74777f] transition-colors hover:text-[#1a1c1e]"
        >
          Настройки
        </button>
        <IconChevronRight size={14} className="text-[#b0b3b8]" />
        {resourceLabel ? (
          <button
            type="button"
            onClick={() => navigate('/admin/settings/references')}
            className="leading-none text-[#74777f] transition-colors hover:text-[#1a1c1e]"
          >
            Справочники
          </button>
        ) : (
          <span className="font-medium leading-none text-[#1a1c1e]">Справочники</span>
        )}
        {resourceLabel && (
          <>
            <IconChevronRight size={14} className="text-[#b0b3b8]" />
            {tail ? (
              <button
                type="button"
                onClick={() => navigate(`/admin/settings/references/${resource}`)}
                className="leading-none text-[#74777f] transition-colors hover:text-[#1a1c1e]"
              >
                {resourceLabel}
              </button>
            ) : (
              <span className="font-medium leading-none text-[#1a1c1e]">{resourceLabel}</span>
            )}
          </>
        )}
        {tail && (
          <>
            <IconChevronRight size={14} className="text-[#b0b3b8]" />
            <span className="font-medium leading-none text-[#1a1c1e]">
              {tail === 'create' ? 'Создание' : 'Редактирование'}
            </span>
          </>
        )}
      </nav>
    );
  }

  // Оперативные сущности: /admin/users/:id, /admin/units/:id —
  // простая ссылка «Назад к списку ...»
  const operationalMatch = pathname.match(/^\/admin\/([^/]+)\/([^/]+)$/);
  if (operationalMatch) {
    const resource = operationalMatch[1];
    const backLabel = OPERATIONAL_BACK_LABELS[resource];
    if (!backLabel) return null;

    return (
      <nav className="flex items-center gap-1.5 text-xs leading-none">
        <button
          type="button"
          onClick={() => navigate(`/admin/${resource}`)}
          className="leading-none text-[#74777f] transition-colors hover:text-[#1a1c1e]"
        >
          {backLabel}
        </button>
      </nav>
    );
  }

  return null;
}
