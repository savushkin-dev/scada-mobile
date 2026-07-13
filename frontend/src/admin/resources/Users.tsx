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
import { AdminChip } from '../ui/AdminChip';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { UnitAssignmentSelect } from '../ui/UnitAssignmentSelect';
import { PillButton } from '../ui/PillButton';
import { AdminDeleteButton } from '../ui/AdminDeleteButton';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { IconPencil } from '../ui/icons';
import { UserNotificationSettingsEditor } from '../components/UserNotificationSettingsEditor';

interface User {
  id: number;
  code: string;
  fullName: string;
  roleId: number;
  active: boolean;
  unitNames?: string[];
  incidentNotificationsCount?: number;
  callNotificationsCount?: number;
}

export const UserList = () => {
  const navigate = useNavigate();
  const { data } = useListContext<User>();
  const records = data ?? [];

  return (
    <AdminListContainer title="Сотрудники">
      <MobileCardList
        records={records}
        renderCard={(user) => (
          <div className="rounded-[20px] bg-white p-4">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-base font-bold text-[#1a1c1e]">
                {formatEmpty(user.fullName)}
              </span>
              <StatusPill variant={user.active ? 'active' : 'inactive'}>
                {user.active ? 'Активен' : 'Неактивен'}
              </StatusPill>
            </div>
            <div className="mb-3 space-y-1 text-sm">
              <div className="flex justify-between">
                <span className="text-[#74777f]">Код</span>
                <span className="text-[#1a1c1e]">{formatEmpty(user.code)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#74777f]">Роль</span>
                <RoleName id={user.roleId} />
              </div>
            </div>
            {user.unitNames && user.unitNames.length > 0 && (
              <div className="mb-3 flex flex-wrap gap-1">
                {user.unitNames.slice(0, 2).map((name) => (
                  <AdminChip key={name}>{name}</AdminChip>
                ))}
                {user.unitNames.length > 2 && <AdminChip>+{user.unitNames.length - 2}</AdminChip>}
              </div>
            )}
            <div className="flex items-center justify-between gap-2">
              <PillButton
                variant="secondary"
                icon={<IconPencil size={16} />}
                onClick={() => navigate(user.id.toString())}
                className="h-9 px-3 text-xs"
              >
                Изменить
              </PillButton>
              <AdminDeleteButton record={user} size="small" />
            </div>
          </div>
        )}
      />
      <DesktopDataTable
        records={records}
        keyExtractor={(user) => user.id}
        columns={[
          { key: 'id', header: 'ID', render: (user) => user.id, className: 'w-12' },
          { key: 'code', header: 'Код сотрудника', render: (user) => user.code },
          { key: 'fullName', header: 'ФИО', render: (user) => user.fullName },
          {
            key: 'role',
            header: 'Роль',
            render: (user) => <RoleName id={user.roleId} />,
          },
          {
            key: 'active',
            header: 'Активен',
            render: (user) => (
              <StatusPill variant={user.active ? 'active' : 'inactive'}>
                {user.active ? 'Активен' : 'Неактивен'}
              </StatusPill>
            ),
          },
          {
            key: 'units',
            header: 'Автоматы',
            render: (user) => (
              <div className="flex flex-wrap gap-1">
                {user.unitNames?.slice(0, 2).map((name) => (
                  <AdminChip key={name}>{name}</AdminChip>
                ))}
                {(user.unitNames?.length ?? 0) > 2 && (
                  <AdminChip>+{(user.unitNames?.length ?? 0) - 2}</AdminChip>
                )}
              </div>
            ),
          },
          {
            key: 'incidents',
            header: 'Тех. сбои',
            render: (user) => user.incidentNotificationsCount ?? 0,
            className: 'w-20',
          },
          {
            key: 'calls',
            header: 'Вызов',
            render: (user) => user.callNotificationsCount ?? 0,
            className: 'w-16',
          },
          {
            key: 'actions',
            header: '',
            render: (user) => (
              <div className="flex items-center justify-end gap-2">
                <PillButton
                  variant="secondary"
                  icon={<IconPencil size={16} />}
                  onClick={() => navigate(user.id.toString())}
                  className="h-9 px-3 text-xs"
                >
                  Изменить
                </PillButton>
                <AdminDeleteButton record={user} size="small" />
              </div>
            ),
          },
        ]}
      />
    </AdminListContainer>
  );
};

function RoleName({ id }: { id: number }) {
  const getName = useNameMap('roles');
  return <span className="text-[#1a1c1e]">{formatEmpty(getName(id))}</span>;
}

function UserFormFields({
  record,
  onChange,
  isCreate,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
  isCreate: boolean;
}) {
  return (
    <div className="space-y-5">
      <RoundedInput
        label="Код сотрудника"
        value={(record.code as string) ?? ''}
        onChange={(e) => onChange('code', e.target.value)}
        required
      />
      <RoundedInput
        label="ФИО"
        value={(record.fullName as string) ?? ''}
        onChange={(e) => onChange('fullName', e.target.value)}
        required
      />
      <RoundedInput
        label={isCreate ? 'Пароль' : 'Пароль (оставьте пустым, чтобы не менять)'}
        type="password"
        value={(record.password as string) ?? ''}
        onChange={(e) => onChange('password', e.target.value)}
        required={isCreate}
      />
      <ReferenceSelect
        label="Роль"
        reference="roles"
        optionText="name"
        value={(record.roleId as number) ?? null}
        onChange={(v) => onChange('roleId', v)}
        placeholder="Выберите роль"
      />
      <label className="flex items-center justify-between">
        <span className="text-sm font-medium text-[#1a1c1e]">Активен</span>
        <IOSSwitch
          checked={!!record.active}
          onChange={(e) => onChange('active', e.target.checked)}
        />
      </label>
      <UnitAssignmentSelect
        value={(record.unitIds as number[]) ?? []}
        onChange={(v) => onChange('unitIds', v)}
      />
    </div>
  );
}

export const UserEdit = () => (
  <AdminEditForm title="Редактирование сотрудника">
    {({ record, onChange }) => (
      <div className="space-y-6">
        <UserFormFields record={record} onChange={onChange} isCreate={false} />
        <UserNotificationSettingsEditor />
      </div>
    )}
  </AdminEditForm>
);

export const UserCreate = () => (
  <AdminCreateForm title="Новый сотрудник" defaultValues={{ active: true, unitIds: [] }}>
    {({ record, onChange }) => <UserFormFields record={record} onChange={onChange} isCreate />}
  </AdminCreateForm>
);
