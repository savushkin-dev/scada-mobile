import { useLocation, useNavigate } from 'react-router-dom';
import { adminMenuItems } from './AdminMenuConfig';
import { useAdminNotificationsCount } from './AdminNotificationsContext';

export function AdminSidebarDesktop() {
  const location = useLocation();
  const navigate = useNavigate();
  const { unreadCount } = useAdminNotificationsCount();

  return (
    <aside className="hidden h-full w-[240px] shrink-0 flex-col border-r border-[#f0f0f0] bg-white lg:flex">
      <div className="flex h-[88px] items-center px-6">
        <span className="text-lg font-bold text-[#1a1c1e]">SCADA Mobile</span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
        {adminMenuItems.map((item) => {
          const isActive = location.pathname.startsWith(`/admin/${item.name}`);
          const isNotifications = item.name === 'notifications';
          return (
            <button
              key={item.name}
              type="button"
              onClick={() => navigate(`/admin/${item.name}`)}
              className={
                'flex items-center gap-3 rounded-[12px] px-4 py-3 text-sm font-medium transition-colors ' +
                (isActive
                  ? 'bg-[#f0f7ff] text-[#4285f4] '
                  : 'text-[#74777f] hover:bg-[#f8f9fa] hover:text-[#1a1c1e] ')
              }
            >
              <span className={isActive ? 'text-[#4285f4]' : 'text-[#74777f]'}>{item.icon}</span>
              <span className="flex-1 text-left">{item.label}</span>
              {isNotifications && unreadCount > 0 && (
                <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-[#ea4335] px-1.5 text-[11px] font-bold text-white">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
