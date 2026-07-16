import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useListContext, useNotify } from 'react-admin';
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
import { GeneratedCredentialsDialog } from '../ui/GeneratedCredentialsDialog';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { formatEmpty } from '../ui/formatEmpty';
import { useNameMap } from '../ui/useNameMap';
import { IconPencil, IconKey } from '../ui/icons';
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
      {!isCreate && (
        <RoundedInput
          label="Код сотрудника"
          value={(record.code as string) ?? ''}
          disabled
          hint="Код генерируется автоматически и не может быть изменён"
        />
      )}
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
      extraActions={(record) =>
        getRoleName(record.roleId as number | undefined) === 'ADMIN' ? null : (
          <PillButton
            variant="secondary"
            icon={<IconKey size={18} />}
            onClick={() => setShowResetConfirm(true)}
            disabled={resetting}
          >
            {resetting ? 'Сброс...' : 'Сбросить пароль'}
          </PillButton>
        )
      }
    >
      {({ record, onChange }) => (
        <div className="space-y-6">
          <UserFormFields record={record} onChange={onChange} isCreate={false} />
          <UserNotificationSettingsEditor />
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
      )}
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
        defaultValues={{ active: true, unitIds: [] }}
        onSuccessWithData={handleSuccess}
      >
        {({ record, onChange }) => <UserFormFields record={record} onChange={onChange} isCreate />}
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
