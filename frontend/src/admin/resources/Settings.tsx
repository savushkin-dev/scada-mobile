import { useNavigate } from 'react-router-dom';
import { AdminCard } from '../ui/AdminCard';
import { IconChevronRight, IconBooks } from '../ui/icons';

export function SettingsPage() {
  const navigate = useNavigate();

  return (
    <div className="p-3 lg:p-4">
      <div className="mb-3 lg:mb-4">
        <h1 className="text-xl font-bold text-[#1a1c1e]">Настройки</h1>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <AdminCard
          title="Справочники системы"
          subtitle="Роли, цеха, типы и справочник устройств"
          icon={<IconBooks size={24} />}
          className="cursor-pointer transition-shadow hover:shadow-[0_4px_16px_rgba(0,0,0,0.06)]"
        >
          <button
            type="button"
            onClick={() => navigate('/admin/settings/references')}
            className="mt-3 flex w-full items-center justify-between rounded-[12px] bg-[#f8f9fa] px-4 py-3 text-sm font-semibold text-[#1a1c1e] transition-colors hover:bg-[#f0f7ff] hover:text-[#4285f4]"
          >
            <span>Перейти к справочникам</span>
            <IconChevronRight size={18} />
          </button>
        </AdminCard>
      </div>
    </div>
  );
}
