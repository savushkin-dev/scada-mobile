import { useListContext, useRefresh, useUpdate } from 'react-admin';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { WS_BASE } from '../../config';
import { getAccessToken } from '../../auth/session';
import { AdminListContainer } from '../ui/AdminListContainer';
import { PillButton } from '../ui/PillButton';
import { StatusPill } from '../ui/StatusPill';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { IconRefresh, IconCheck, IconPencil } from '../ui/icons';
import { useAdminNotificationsCount } from '../ui/AdminNotificationsContext';
import { NOTIFICATION_FILTER_FIELDS } from '../filters/configs';
import { useTableFilters } from '../filters/TableFilterContext';

interface Notification {
  id: number | string;
  type: string;
  severity: string;
  instanceId?: string | null;
  deviceCode?: string;
  catalogId?: number | null;
  userId?: number | null;
  message: string;
  read: boolean;
  createdAt: string;
}

function severityVariant(severity: string): 'warning' | 'error' | 'inactive' {
  switch (severity) {
    case 'WARNING':
      return 'error';
    case 'INFO':
    default:
      return 'warning';
  }
}

function typeLabel(type: string) {
  switch (type) {
    case 'DEVICE_DISCOVERED':
      return 'Новое устройство';
    case 'DEVICE_DISCONNECTED':
      return 'Устройство отключено';
    case 'DEVICE_RECONNECTED':
      return 'Устройство подключено';
    case 'PASSWORD_CHANGED':
      return 'Смена пароля';
    case 'USER_INACTIVE':
      return 'Бездействие пользователя';
    default:
      return type;
  }
}

function useMarkAsRead() {
  const [update, { isPending }] = useUpdate();
  const refresh = useRefresh();
  const { refreshCount } = useAdminNotificationsCount();

  const mark = (id: number | string, onSuccess?: () => void) => {
    update(
      'notifications',
      { id, data: {} },
      {
        onSuccess: () => {
          refresh();
          refreshCount();
          onSuccess?.();
        },
      }
    );
  };

  return { mark, isPending };
}

function MarkAsReadButton({ id }: { id: number | string }) {
  const { mark, isPending } = useMarkAsRead();
  return (
    <PillButton
      variant="secondary"
      icon={<IconCheck size={16} />}
      onClick={() => mark(id)}
      disabled={isPending}
      className="h-9 px-3 text-xs"
    >
      Прочитано
    </PillButton>
  );
}

function DiscoveredDeviceActions({ note }: { note: Notification }) {
  const navigate = useNavigate();
  const { mark, isPending } = useMarkAsRead();

  return (
    <div className="flex flex-wrap items-center gap-2">
      <PillButton
        variant="secondary"
        icon={<IconCheck size={16} />}
        onClick={() => mark(note.id)}
        disabled={isPending}
        className="h-9 px-3 text-xs"
      >
        Игнорировать
      </PillButton>
      <PillButton
        variant="primary"
        icon={<IconPencil size={16} />}
        onClick={() =>
          mark(note.id, () => {
            if (note.catalogId) {
              navigate(`/admin/device-catalog/${note.catalogId}`);
            }
          })
        }
        disabled={isPending || !note.catalogId}
        className="h-9 px-3 text-xs"
      >
        Внести
      </PillButton>
    </div>
  );
}

function NotificationActions({ note }: { note: Notification }) {
  if (note.read) {
    return null;
  }

  const isNewDeviceWarning = note.type === 'DEVICE_DISCOVERED' && note.severity === 'WARNING';

  if (isNewDeviceWarning) {
    return <DiscoveredDeviceActions note={note} />;
  }

  return <MarkAsReadButton id={note.id} />;
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        'rounded-full px-4 py-2 text-sm font-semibold transition-colors ' +
        (active
          ? 'bg-[#1a1c1e] text-white shadow-[0_2px_8px_rgba(26,28,30,0.12)]'
          : 'bg-white text-[#74777f] hover:bg-[#f8f9fa] hover:text-[#1a1c1e]')
      }
    >
      {children}
    </button>
  );
}

export function NotificationList() {
  const refresh = useRefresh();
  const { data } = useListContext<Notification>();
  const records = data ?? [];

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;

    const wsUrl = `${WS_BASE}/ws/live?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(wsUrl);

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'ADMIN_NOTIFICATION') refresh();
      } catch {
        // ignore
      }
    };
    return () => ws.close();
  }, [refresh]);

  const filters = (
    <div className="flex flex-wrap items-center gap-2">
      <ArchiveToggle />
      <PillButton
        variant="secondary"
        icon={<IconRefresh size={16} />}
        onClick={refresh}
        className="h-9 px-3 text-xs"
      >
        Обновить
      </PillButton>
    </div>
  );

  return (
    <AdminListContainer
      title="Уведомления"
      records={records}
      showCreate={false}
      filterFields={NOTIFICATION_FILTER_FIELDS}
      filters={filters}
    >
      {({ records: filtered }) => (
        <>
          <MobileCardList
            records={filtered}
            renderCard={(note) => (
              <div className="rounded-[20px] bg-white p-4">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-semibold text-[#1a1c1e]">
                    {typeLabel(note.type)}
                  </span>
                  <StatusPill variant={severityVariant(note.severity)}>{note.severity}</StatusPill>
                </div>
                <div className="mb-1 text-sm text-[#1a1c1e]">{note.message}</div>
                <div className="mb-3 text-xs text-[#74777f]">
                  {[
                    note.instanceId,
                    note.deviceCode,
                    new Date(note.createdAt).toLocaleString('ru-RU'),
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
                <NotificationActions note={note} />
              </div>
            )}
          />
          <DesktopDataTable
            records={filtered}
            keyExtractor={(note) => note.id}
            columns={[
              {
                key: 'type',
                header: 'Тип',
                filterKey: 'type',
                render: (note) => typeLabel(note.type),
              },
              {
                key: 'severity',
                header: 'Важность',
                filterKey: 'severity',
                render: (note) => (
                  <StatusPill variant={severityVariant(note.severity)}>{note.severity}</StatusPill>
                ),
              },
              {
                key: 'instance',
                header: 'Автомат',
                filterKey: 'instanceId',
                render: (note) => note.instanceId ?? '—',
              },
              {
                key: 'device',
                header: 'Устройство',
                filterKey: 'deviceCode',
                render: (note) => note.deviceCode ?? '—',
              },
              {
                key: 'message',
                header: 'Сообщение',
                filterKey: 'message',
                render: (note) => note.message,
              },
              {
                key: 'time',
                header: 'Время',
                filterKey: 'createdAt',
                render: (note) => new Date(note.createdAt).toLocaleString('ru-RU'),
              },
              {
                key: 'action',
                header: '',
                render: (note) => <NotificationActions note={note} />,
              },
            ]}
          />
        </>
      )}
    </AdminListContainer>
  );
}

/**
 * Переключатель «Архив» — фильтр по полю read, выполняется на бэкенде.
 * По умолчанию видны только непрочитанные; активная «Архив» показывает
 * прочитанные. Состояние хранится в URL (filterValues).
 */
function ArchiveToggle() {
  const ctx = useTableFilters();

  // По умолчанию — только непрочитанные
  useEffect(() => {
    if (ctx && ctx.filterValues.f?.read === undefined) {
      ctx.setFieldFilter('read', 'false');
    }
  }, [ctx]);

  if (!ctx) return null;

  const archiveActive = ctx.filterValues.f?.read === 'true';

  return (
    <TabButton
      active={archiveActive}
      onClick={() => ctx.setFieldFilter('read', archiveActive ? 'false' : 'true')}
    >
      Архив
    </TabButton>
  );
}
