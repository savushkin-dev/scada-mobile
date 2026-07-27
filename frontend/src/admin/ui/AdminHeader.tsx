import { useLocation, useNavigate } from 'react-router-dom';
import { useAdminNotificationsCount } from './AdminNotificationsContext';
import { IconSettings, IconNotifications, IconUserTie } from './icons';

const operationalItems = [
  { name: 'users', label: 'Сотрудники' },
  { name: 'units', label: 'Автоматы' },
];

export function AdminHeader() {
  const location = useLocation();
  const navigate = useNavigate();
  const { unreadCount } = useAdminNotificationsCount();

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-[#f0f0f0] bg-white px-4 lg:px-6">
      <div className="flex items-center gap-6">
        <button
          type="button"
          onClick={() => navigate('/admin/users')}
          className="flex flex-col items-start leading-tight"
        >
          <span className="text-base font-bold text-[#1a1c1e]">SCADA Mobile</span>
          <span className="text-[10px] font-semibold uppercase tracking-wider text-[#74777f]">
            Администрирование
          </span>
        </button>

        <nav className="hidden items-center gap-1 lg:flex">
          {operationalItems.map((item) => {
            const isActive = location.pathname.startsWith(`/admin/${item.name}`);
            return (
              <button
                key={item.name}
                type="button"
                onClick={() => navigate(`/admin/${item.name}`)}
                className={
                  'rounded-[10px] px-4 py-2 text-sm font-semibold transition-colors ' +
                  (isActive
                    ? 'bg-[#1a1c1e] text-white'
                    : 'text-[#74777f] hover:bg-[#f8f9fa] hover:text-[#1a1c1e]')
                }
              >
                {item.label}
              </button>
            );
          })}
        </nav>
      </div>

      <div className="flex items-center gap-2">
        <HeaderIconButton
          active={location.pathname.startsWith('/admin/settings')}
          onClick={() => navigate('/admin/settings')}
          ariaLabel="Настройки"
        >
          <IconSettings size={20} />
        </HeaderIconButton>
        <HeaderIconButton
          active={location.pathname.startsWith('/admin/notifications')}
          onClick={() => navigate('/admin/notifications')}
          ariaLabel="Уведомления"
          badge={unreadCount}
        >
          <IconNotifications size={20} />
        </HeaderIconButton>
        <HeaderIconButton
          active={location.pathname.startsWith('/profile')}
          onClick={() => navigate('/profile')}
          ariaLabel="Профиль"
        >
          <IconUserTie size={20} />
        </HeaderIconButton>
      </div>
    </header>
  );
}

function HeaderIconButton({
  active,
  onClick,
  ariaLabel,
  badge,
  children,
}: {
  active: boolean;
  onClick: () => void;
  ariaLabel: string;
  badge?: number;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      aria-pressed={active}
      className={
        'relative flex h-10 w-10 items-center justify-center rounded-full transition-colors ' +
        (active ? 'bg-[#1a1c1e] text-white' : 'text-[#1a1c1e] hover:bg-[#f8f9fa]')
      }
    >
      {children}
      {!!badge && badge > 0 && (
        <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-[#ea4335] px-1 text-[9px] font-bold text-white shadow-sm">
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </button>
  );
}
