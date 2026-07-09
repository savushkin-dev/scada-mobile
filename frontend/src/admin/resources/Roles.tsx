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

interface Role {
  id: number;
  name: string;
}

export const RoleList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<Role>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Роли">
      <MobileCardList
        records={records}
        renderCard={(role) => (
          <div className="rounded-[20px] bg-white p-4 shadow-[0_2px_8px_rgba(0,0,0,0.03)]">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-base font-bold text-[#1a1c1e]">{role.name}</span>
            </div>
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(role.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <PillButton
                variant="danger"
                icon={<IconTrash size={16} />}
                onClick={() => navigate(role.id.toString())}
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
        keyExtractor={(role) => role.id}
        columns={[
          { key: 'id', header: 'ID', render: (role) => role.id, className: 'w-16' },
          { key: 'name', header: 'Название', render: (role) => role.name },
          {
            key: 'actions',
            header: '',
            render: (role) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(role.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <PillButton
                  variant="danger"
                  icon={<IconTrash size={16} />}
                  onClick={() => navigate(role.id.toString())}
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

export const RoleEdit = () => (
  <AdminEditForm title="Редактирование роли">
    {({ record, onChange }) => (
      <RoundedInput
        label="Название роли"
        value={(record.name as string) ?? ''}
        onChange={(e) => onChange('name', e.target.value)}
        required
      />
    )}
  </AdminEditForm>
);

export const RoleCreate = () => (
  <AdminCreateForm title="Новая роль">
    {({ record, onChange }) => (
      <RoundedInput
        label="Название роли"
        value={(record.name as string) ?? ''}
        onChange={(e) => onChange('name', e.target.value)}
        required
      />
    )}
  </AdminCreateForm>
);
