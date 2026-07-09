import { useLocation, useNavigate } from 'react-router-dom';
import { useAdminNav } from './useAdminNav';
import { IconChevronLeft, IconMenu } from './icons';
import { adminMenuItems } from './AdminMenuConfig';

export function AdminMobileHeader() {
  const { openMenu } = useAdminNav();
  const location = useLocation();
  const navigate = useNavigate();

  const isEditOrCreate = /\/(edit|create)\/?$/.test(location.pathname);
  const resourceName = adminMenuItems.find((item) =>
    location.pathname.startsWith(`/admin/${item.name}`)
  );

  const title = resourceName?.label ?? 'Админ-панель';

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#f0f0f0] bg-white px-4 lg:hidden">
      <div className="flex items-center gap-2">
        {isEditOrCreate ? (
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="flex h-9 w-9 items-center justify-center rounded-full text-[#1a1c1e] transition-colors hover:bg-[#f8f9fa]"
            aria-label="Назад"
          >
            <IconChevronLeft size={20} />
          </button>
        ) : null}
        <h1 className="text-base font-bold text-[#1a1c1e]">{title}</h1>
      </div>
      <button
        type="button"
        onClick={openMenu}
        className="flex h-9 w-9 items-center justify-center rounded-full text-[#1a1c1e] transition-colors hover:bg-[#f8f9fa]"
        aria-label="Меню"
      >
        <IconMenu size={20} />
      </button>
    </header>
  );
}
