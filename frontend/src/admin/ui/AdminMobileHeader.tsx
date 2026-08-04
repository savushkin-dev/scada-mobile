import { useLocation } from 'react-router-dom';
import { useAdminNav } from './useAdminNav';
import { IconMenu, IconUserTie } from './icons';
import { adminMenuItems, adminOperationalItems, adminReferenceItems } from './AdminMenuConfig';

// Более специфичные пути идут раньше: find() возвращает первое совпадение
// по префиксу, поэтому «settings/references/roles» должен стоять до
// общего «settings/references».
const allMenuItems = [
  ...adminOperationalItems,
  ...adminReferenceItems.map((r) => ({ ...r, name: `settings/references/${r.name}` })),
  ...adminMenuItems,
  { name: 'profile', label: 'Профиль', icon: <IconUserTie size={20} /> },
];

export function AdminMobileHeader() {
  const { openMenu } = useAdminNav();
  const location = useLocation();

  const matched = allMenuItems.find((item) => location.pathname.startsWith(`/admin/${item.name}`));
  const title = matched?.label ?? 'Админ-панель';

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#f0f0f0] bg-white px-4 lg:hidden">
      <div className="flex items-center gap-2">
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
