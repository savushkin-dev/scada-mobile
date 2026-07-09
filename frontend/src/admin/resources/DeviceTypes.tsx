import { useNavigate } from 'react-router-dom';
import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { RoundedInput } from '../ui/RoundedInput';
import { PillButton } from '../ui/PillButton';
import { IconPencil, IconTrash } from '../ui/icons';

interface DeviceType {
  id: number;
  code: string;
  name: string;
}

export const DeviceTypeList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<DeviceType>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Типы устройств">
      <MobileCardList
        records={records}
        renderCard={(type) => (
          <div className="rounded-[20px] bg-white p-4 shadow-[0_2px_8px_rgba(0,0,0,0.03)]">
            <div className="mb-1">
              <span className="text-base font-bold text-[#1a1c1e]">{type.name}</span>
            </div>
            <div className="mb-3 text-sm text-[#74777f]">{type.code}</div>
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(type.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <PillButton
                variant="danger"
                icon={<IconTrash size={16} />}
                onClick={() => navigate(type.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Удалить
              </PillButton>
            </div>
          </div>
        )}
      />
      <DesktopDataTable
        records={records}
        keyExtractor={(type) => type.id}
        columns={[
          { key: 'id', header: 'ID', render: (type) => type.id, className: 'w-16' },
          { key: 'code', header: 'Код', render: (type) => type.code },
          { key: 'name', header: 'Название', render: (type) => type.name },
          {
            key: 'actions',
            header: '',
            render: (type) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(type.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <PillButton
                  variant="danger"
                  icon={<IconTrash size={16} />}
                  onClick={() => navigate(type.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Удалить
                </PillButton>
              </div>
            ),
          },
        ]}
      />
    </AdminListContainer>
  );
};

export const DeviceTypeEdit = () => (
  <AdminEditForm title="Редактирование типа устройства">
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Код типа"
          value={(record.code as string) ?? ''}
          onChange={(e) => onChange('code', e.target.value)}
          required
        />
        <RoundedInput
          label="Название типа"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
      </div>
    )}
  </AdminEditForm>
);

export const DeviceTypeCreate = () => (
  <AdminCreateForm title="Новый тип устройства">
    {({ record, onChange }) => (
      <div className="space-y-5">
        <RoundedInput
          label="Код типа"
          value={(record.code as string) ?? ''}
          onChange={(e) => onChange('code', e.target.value)}
          required
        />
        <RoundedInput
          label="Название типа"
          value={(record.name as string) ?? ''}
          onChange={(e) => onChange('name', e.target.value)}
          required
        />
      </div>
    )}
  </AdminCreateForm>
);
