import { useLocation, useNavigate } from 'react-router-dom';
import { useAdminNav } from './useAdminNav';
import { BottomSheet } from './BottomSheet';
import { adminMenuItems } from './AdminMenuConfig';

export function AdminBottomSheetMenu() {
  const { isMenuOpen, closeMenu } = useAdminNav();
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <BottomSheet isOpen={isMenuOpen} onClose={closeMenu} title="Меню">
      <nav className="flex flex-col gap-1">
        {adminMenuItems.map((item) => {
          const isActive = location.pathname.startsWith(`/admin/${item.name}`);
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
              {item.label}
            </button>
          );
        })}
      </nav>
    </BottomSheet>
  );
}
