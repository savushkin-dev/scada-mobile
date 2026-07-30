import { useListContext } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { RoundedInput } from '../ui/RoundedInput';
import { formatEmpty } from '../ui/formatEmpty';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { useRowActions } from '../ui/useRowActions';
import { ROLE_FILTER_FIELDS } from '../filters/configs';

interface Role {
  id: number;
  name: string;
}

export const RoleList = () => {
  const { navigateToEdit, deleteRecord } = useRowActions();
  const { data } = useListContext<Role>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Роли" records={records} filterFields={ROLE_FILTER_FIELDS}>
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(role) => (
              <div className="rounded-[20px] bg-white p-4">
                <div className="mb-3">
                  <span className="text-base font-bold text-[#1a1c1e]">
                    {formatEmpty(role.name)}
                  </span>
                </div>
                <div className="flex items-center justify-end">
                  <RowActionsMenu
                    onEdit={() => navigateToEdit(role.id)}
                    onDelete={() => deleteRecord(role)}
                  />
                </div>
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(role) => role.id}
            columns={[
              {
                key: 'id',
                header: 'ID',
                render: (role) => role.id,
                className: 'w-16',
                filterKey: 'id',
              },
              { key: 'name', header: 'Название', render: (role) => role.name, filterKey: 'name' },
              {
                key: 'actions',
                header: '',
                render: (role) => (
                  <div className="flex items-center justify-end">
                    <RowActionsMenu
                      onEdit={() => navigateToEdit(role.id)}
                      onDelete={() => deleteRecord(role)}
                    />
                  </div>
                ),
              },
            ]}
          />
        </>
      )}
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

export const RoleCreate = ({
  onSuccessWithData,
}: {
  onSuccessWithData?: (data: Record<string, unknown>) => void;
}) => (
  <AdminCreateForm title="Новая роль" onSuccessWithData={onSuccessWithData}>
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
