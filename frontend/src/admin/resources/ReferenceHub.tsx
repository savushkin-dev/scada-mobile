import { useNavigate } from 'react-router-dom';
import { AdminCard } from '../ui/AdminCard';
import { adminReferenceItems } from '../ui/AdminMenuConfig';
import {
  IconChevronRight,
  IconRoles,
  IconWorkshops,
  IconDeviceTypes,
  IconDevices,
} from '../ui/icons';

const referenceIcons: Record<string, React.ReactNode> = {
  roles: <IconRoles size={24} />,
  workshops: <IconWorkshops size={24} />,
  'device-types': <IconDeviceTypes size={24} />,
  'device-catalog': <IconDevices size={24} />,
};

export function ReferenceHubPage() {
  const navigate = useNavigate();

  return (
    <div className="p-4 lg:p-6">
      <div className="mb-4 lg:mb-6">
        <h1 className="text-xl font-bold text-[#1a1c1e]">Справочники</h1>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {adminReferenceItems.map((item) => (
          <AdminCard
            key={item.name}
            title={item.label}
            icon={referenceIcons[item.name]}
            className="cursor-pointer transition-shadow hover:shadow-[0_4px_16px_rgba(0,0,0,0.06)]"
          >
            <button
              type="button"
              onClick={() => navigate(`/admin/settings/references/${item.name}`)}
              className="mt-3 flex w-full items-center justify-between rounded-[12px] bg-[#f8f9fa] px-4 py-3 text-sm font-semibold text-[#1a1c1e] transition-colors hover:bg-[#f0f7ff] hover:text-[#4285f4]"
            >
              <span>Открыть</span>
              <IconChevronRight size={18} />
            </button>
          </AdminCard>
        ))}
      </div>
    </div>
  );
}
