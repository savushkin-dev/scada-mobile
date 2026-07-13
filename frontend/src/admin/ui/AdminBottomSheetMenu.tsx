import { useLocation, useNavigate } from 'react-router-dom';
import { useAdminNav } from './useAdminNav';
import { BottomSheet } from './BottomSheet';
import { adminMenuItems } from './AdminMenuConfig';
import { useAdminNotificationsCount } from './AdminNotificationsContext';

export function AdminBottomSheetMenu() {
  const { isMenuOpen, closeMenu } = useAdminNav();
  const location = useLocation();
  const navigate = useNavigate();
  const { unreadCount } = useAdminNotificationsCount();

  return (
    <BottomSheet isOpen={isMenuOpen} onClose={closeMenu} title="Меню">
      <nav className="flex flex-col gap-1">
        {adminMenuItems.map((item) => {
          const isActive = location.pathname.startsWith(`/admin/${item.name}`);
          const isNotifications = item.name === 'notifications';
          return (
            <button
              key={item.name}
              type="button"
              onClick={() => {
                navigate(`/admin/${item.name}`);
                closeMenu();
              }}
              className={
                'flex items-center gap-3 rounded-[12px] px-4 py-3.5 text-[15px] font-medium transition-colors ' +
                (isActive ? 'bg-[#f0f7ff] text-[#4285f4] ' : 'text-[#1a1c1e] hover:bg-[#f8f9fa] ')
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
    </BottomSheet>
  );
}
