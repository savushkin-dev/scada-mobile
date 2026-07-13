import { useNavigate } from 'react-router-dom';
import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { RoundedInput } from '../ui/RoundedInput';
import { IOSSwitch } from '../ui/IOSSwitch';
import { StatusPill } from '../ui/StatusPill';
import { PillButton } from '../ui/PillButton';
import { AdminDeleteButton } from '../ui/AdminDeleteButton';
import { formatEmpty } from '../ui/formatEmpty';
import { IconPencil } from '../ui/icons';

interface Workshop {
  id: number;
  name: string;
  active: boolean;
}

export const WorkshopList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<Workshop>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Цеха">
      <MobileCardList
        records={records}
        renderCard={(workshop) => (
          <div className="rounded-[20px] bg-white p-4">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-base font-bold text-[#1a1c1e]">
                {formatEmpty(workshop.name)}
              </span>
              <StatusPill variant={workshop.active ? 'active' : 'inactive'}>
                {workshop.active ? 'Активен' : 'Неактивен'}
              </StatusPill>
            </div>
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(workshop.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <AdminDeleteButton record={workshop} size="small" />
            </div>
          </div>
        )}
      />
      <DesktopDataTable
        records={records}
        keyExtractor={(workshop) => workshop.id}
        columns={[
          { key: 'id', header: 'ID', render: (workshop) => workshop.id, className: 'w-16' },
          { key: 'name', header: 'Название', render: (workshop) => workshop.name },
          {
            key: 'active',
            header: 'Активен',
            render: (workshop) => (
              <StatusPill variant={workshop.active ? 'active' : 'inactive'}>
                {workshop.active ? 'Активен' : 'Неактивен'}
              </StatusPill>
            ),
          },
          {
            key: 'actions',
            header: '',
            render: (workshop) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(workshop.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <AdminDeleteButton record={workshop} size="small" />
              </div>
            ),
          },
        ]}
      />
    </AdminListContainer>
  );
};

export const WorkshopEdit = () => (
  <AdminEditForm title="Редактирование цеха">
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Название цеха"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
        <label className="flex items-center justify-between">
          <span className="text-sm font-medium text-[#1a1c1e]">Активен</span>
          <IOSSwitch
            checked={!!record.active}
            onChange={(e) => onChange('active', e.target.checked)}
          />
        </label>
      </div>
    )}
  </AdminEditForm>
);

export const WorkshopCreate = () => (
  <AdminCreateForm title="Новый цех" defaultValues={{ active: true }}>
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Название цеха"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
        <label className="flex items-center justify-between">
          <span className="text-sm font-medium text-[#1a1c1e]">Активен</span>
          <IOSSwitch
            checked={!!record.active}
            onChange={(e) => onChange('active', e.target.checked)}
          />
        </label>
      </div>
    )}
  </AdminCreateForm>
);
