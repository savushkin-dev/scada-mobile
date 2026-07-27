import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useListContext, useNotify } from 'react-admin';
import { AdminListContainer } from '../ui/AdminListContainer';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { AdminEditForm } from '../ui/AdminEditForm';
import { AdminCreateForm } from '../ui/AdminCreateForm';
import { CreateRecordOverlay } from '../ui/CreateRecordOverlay';
import { RoleCreate } from './Roles';
import { UnitCreate } from './Units';
import { RoundedInput } from '../ui/RoundedInput';
import { AdminChip } from '../ui/AdminChip';
import { ReferenceSelect } from '../ui/ReferenceSelect';
import { UnitAssignmentSelect } from '../ui/UnitAssignmentSelect';
import { GeneratedCredentialsDialog } from '../ui/GeneratedCredentialsDialog';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { IconKey, IconBell } from '../ui/icons';
import { RowActionsMenu } from '../ui/RowActionsMenu';
import { useRowActions } from '../ui/useRowActions';
import { API_BASE } from '../../config';
import { apiFetchJson } from '../../api/client';
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

interface GeneratedCredentials {
  fullName: string;
  code: string;
  password: string;
}

const USER_SEARCHABLE_FIELDS: (keyof User)[] = ['code', 'fullName'];

export const UserList = () => {
  const { navigateToEdit, toggleActive, deleteRecord } = useRowActions();
  const { data } = useListContext<User>();
  const records = data ?? [];

  return (
    <AdminListContainer
      title="Сотрудники"
      records={records}
      searchableFields={USER_SEARCHABLE_FIELDS}
    >
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(user) => (
              <div className={`rounded-[20px] p-4 ${user.active ? 'bg-white' : 'bg-[#f8f9fa]'}`}>
                <div className={user.active ? '' : 'opacity-60'}>
                  <div className="mb-2">
                    <span className="text-base font-bold text-[#1a1c1e]">
                      {formatEmpty(user.fullName)}
                    </span>
                  </div>
                  <div className="mb-3 space-y-1 text-sm">
                    <div className="flex justify-between">
                      <span className="text-[#74777f]">Таб. номер</span>
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
                      {user.unitNames.length > 2 && (
                        <AdminChip>+{user.unitNames.length - 2}</AdminChip>
                      )}
                    </div>
                  )}
                </div>
                <div className="flex items-center justify-end">
                  <RowActionsMenu
                    onEdit={() => navigateToEdit(user.id)}
                    isActive={user.active}
                    onToggleActive={() => toggleActive(user)}
                    onDelete={() => deleteRecord(user)}
                  />
                </div>
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(user) => user.id}
            isActive={(user) => user.active}
            columns={[
              { key: 'id', header: 'ID', render: (user) => user.id, className: 'w-12' },
              { key: 'code', header: 'Таб. номер', render: (user) => user.code, className: 'w-24' },
              { key: 'fullName', header: 'ФИО', render: (user) => user.fullName },
              {
                key: 'role',
                header: 'Роль',
                render: (user) => <RoleName id={user.roleId} />,
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
                  <div className="flex items-center justify-end">
                    <RowActionsMenu
                      onEdit={() => navigateToEdit(user.id)}
                      isActive={user.active}
                      onToggleActive={() => toggleActive(user)}
                      onDelete={() => deleteRecord(user)}
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

function RoleName({ id }: { id: number }) {
  const getName = useNameMap('roles');
  return <span className="text-[#1a1c1e]">{formatEmpty(getName(id))}</span>;
}

function isValidWorkerCode(value: string): boolean {
  return /^\d{0,5}$/.test(value);
}

function WorkerCodeInput({
  value,
  onChange,
  isCreate,
}: {
  value: string;
  onChange: (value: string) => void;
  isCreate: boolean;
}) {
  const [error, setError] = useState<string | undefined>(undefined);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const next = e.target.value;
    if (!isValidWorkerCode(next)) return;
    setError(
      next.length === 5 || next.length === 0
        ? undefined
        : 'Табельный номер должен состоять из 5 цифр'
    );
    onChange(next);
  };

  return (
    <RoundedInput
      label="Табельный номер"
      value={value}
      onChange={handleChange}
      inputMode="numeric"
      pattern="\d{5}"
      maxLength={5}
      required
      hint={isCreate ? 'Введите табельный номер из 5 цифр' : undefined}
      error={error}
    />
  );
}

function UserLeftFields({
  record,
  onChange,
  isCreate,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
  isCreate: boolean;
}) {
  const [creatingRole, setCreatingRole] = useState(false);

  return (
    <div className="space-y-5">
      <WorkerCodeInput
        value={(record.code as string) ?? ''}
        onChange={(v) => onChange('code', v)}
        isCreate={isCreate}
      />
      <RoundedInput
        label="ФИО"
        value={(record.fullName as string) ?? ''}
        onChange={(e) => onChange('fullName', e.target.value)}
        required
      />
      <ReferenceSelect
        label="Роль"
        reference="roles"
        optionText="name"
        value={(record.roleId as number) ?? null}
        onChange={(v) => onChange('roleId', v)}
        placeholder="Выберите роль"
        onAddNew={() => setCreatingRole(true)}
        addNewLabel="Добавить роль"
      />
      {creatingRole && (
        <CreateRecordOverlay
          resource="roles"
          onClose={() => setCreatingRole(false)}
          onCreated={(id) => onChange('roleId', id)}
        >
          {(onSuccess) => <RoleCreate onSuccessWithData={onSuccess} />}
        </CreateRecordOverlay>
      )}
    </div>
  );
}

function UserRightFields({
  record,
  onChange,
}: {
  record: Record<string, unknown>;
  onChange: (field: string, value: unknown) => void;
}) {
  const [creatingUnit, setCreatingUnit] = useState(false);

  return (
    <div className="space-y-5">
      <UnitAssignmentSelect
        value={(record.unitIds as number[]) ?? []}
        onChange={(v) => onChange('unitIds', v)}
        onAddNew={() => setCreatingUnit(true)}
        addNewLabel="Добавить автомат"
      />
      {creatingUnit && (
        <CreateRecordOverlay
          resource="units"
          onClose={() => setCreatingUnit(false)}
          onCreated={(id) => onChange('unitIds', [...((record.unitIds as number[]) ?? []), id])}
        >
          {(onSuccess) => <UnitCreate onSuccessWithData={onSuccess} />}
        </CreateRecordOverlay>
      )}
    </div>
  );
}

export const UserEdit = () => {
  const notify = useNotify();
  const navigate = useNavigate();
  const getRoleName = useNameMap('roles');
  const [credentials, setCredentials] = useState<GeneratedCredentials | null>(null);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [resetting, setResetting] = useState(false);

  const handleResetPassword = async (userId: string | number) => {
    setResetting(true);
    try {
      const response = (await apiFetchJson(
        `${API_BASE}/api/v1.0.0/admin/users/${encodeURIComponent(userId)}/reset-password`,
        { method: 'POST' }
      )) as {
        code: string;
        fullName: string;
        generatedPassword: string;
      };
      setCredentials({
        fullName: response.fullName,
        code: response.code,
        password: response.generatedPassword,
      });
      notify('Пароль сброшен', { type: 'info' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Ошибка сброса пароля';
      notify(message, { type: 'error', autoHideDuration: null });
    } finally {
      setResetting(false);
      setShowResetConfirm(false);
    }
  };

  return (
    <AdminEditForm
      title="Редактирование сотрудника"
      layout="two-column"
      defaultLeftWidth={25}
      menuItems={(record) =>
        getRoleName(record.roleId as number | undefined) !== 'ADMIN'
          ? [
              {
                key: 'reset-password',
                label: resetting ? 'Сброс...' : 'Сбросить пароль',
                icon: <IconKey size={16} />,
                onClick: () => setShowResetConfirm(true),
              },
            ]
          : []
      }
      leftCardTitle="Основная информация"
      rightCardTitle="Настройки уведомлений"
      rightCardIcon={<IconBell size={20} />}
      rightPanelScrollable={false}
    >
      {({ record, onChange, slot }) =>
        slot === 'left' ? (
          <UserLeftFields record={record} onChange={onChange} isCreate={false} />
        ) : (
          <div className="flex flex-1 flex-col min-h-0 gap-4">
            <UserRightFields record={record} onChange={onChange} />
            <UserNotificationSettingsEditor userId={record.id as number | undefined} />
            <ResetPasswordConfirm
              isOpen={showResetConfirm}
              onClose={() => setShowResetConfirm(false)}
              onConfirm={() => handleResetPassword(record.id as string | number)}
            />
            <GeneratedCredentialsDialog
              isOpen={credentials != null}
              fullName={credentials?.fullName ?? ''}
              code={credentials?.code ?? ''}
              password={credentials?.password ?? ''}
              onClose={() => {
                setCredentials(null);
                navigate('/admin/users');
              }}
            />
          </div>
        )
      }
    </AdminEditForm>
  );
};

export const UserCreate = () => {
  const navigate = useNavigate();
  const notify = useNotify();
  const [credentials, setCredentials] = useState<GeneratedCredentials | null>(null);

  const handleSuccess = (data: Record<string, unknown>) => {
    setCredentials({
      fullName: String(data.fullName ?? ''),
      code: String(data.code ?? ''),
      password: String(data.generatedPassword ?? ''),
    });
  };

  return (
    <>
      <AdminCreateForm
        title="Новый сотрудник"
        layout="two-column"
        defaultValues={{ code: '', active: true, unitIds: [] }}
        onSuccessWithData={handleSuccess}
        leftCardTitle="Основная информация"
        rightCardTitle="Закреплённые автоматы"
      >
        {({ record, onChange, slot }) =>
          slot === 'left' ? (
            <UserLeftFields record={record} onChange={onChange} isCreate />
          ) : (
            <UserRightFields record={record} onChange={onChange} />
          )
        }
      </AdminCreateForm>
      <GeneratedCredentialsDialog
        isOpen={credentials != null}
        fullName={credentials?.fullName ?? ''}
        code={credentials?.code ?? ''}
        password={credentials?.password ?? ''}
        onClose={() => {
          setCredentials(null);
          notify('Создано', { type: 'info' });
          navigate('/admin/users');
        }}
      />
    </>
  );
};

function ResetPasswordConfirm({
  isOpen,
  onClose,
  onConfirm,
}: {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 1024);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  return (
    <ConfirmDialog
      isOpen={isOpen}
      onClose={onClose}
      onConfirm={onConfirm}
      title="Сбросить пароль?"
      message="Старый пароль сотрудника сразу перестанет работать. Система сгенерирует новый временный пароль."
      confirmText="Сбросить"
      cancelText="Отмена"
      isMobile={isMobile}
    />
  );
}
