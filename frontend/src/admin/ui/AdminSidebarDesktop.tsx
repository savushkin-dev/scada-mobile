import { useLocation, useNavigate } from 'react-router-dom';
import { adminMenuItems } from './AdminMenuConfig';

export function AdminSidebarDesktop() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <aside className="hidden h-full w-[240px] shrink-0 flex-col border-r border-[#f0f0f0] bg-white lg:flex">
      <div className="flex h-[88px] items-center px-6">
        <span className="text-lg font-bold text-[#1a1c1e]">SCADA Mobile</span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
        {adminMenuItems.map((item) => {
          const isActive = location.pathname.startsWith(`/admin/${item.name}`);
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
              {item.label}
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
