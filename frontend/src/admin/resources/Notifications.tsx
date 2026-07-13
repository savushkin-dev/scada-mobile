import { useListContext, useRefresh, useUpdate } from 'react-admin';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { WS_BASE } from '../../config';
import { getAccessToken } from '../../auth/session';
import { AdminCard } from '../ui/AdminCard';

import { PillButton } from '../ui/PillButton';
import { StatusPill } from '../ui/StatusPill';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { IconRefresh, IconCheck, IconPencil } from '../ui/icons';
import { useAdminNotificationsCount } from '../ui/AdminNotificationsContext';

interface Notification {
  id: number | string;
  type: string;
  severity: string;
  instanceId: string;
  deviceCode?: string;
  catalogId?: number | null;
  message: string;
  read: boolean;
  createdAt: string;
}

type Tab = 'unread' | 'read';

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

function MarkAllAsReadButton() {
  const [update, { isPending }] = useUpdate();
  const refresh = useRefresh();
  const { refreshCount } = useAdminNotificationsCount();

  return (
    <PillButton
      variant="secondary"
      icon={<IconCheck size={16} />}
      onClick={() =>
        update(
          'notifications',
          { id: 'read-all', data: {} },
          {
            onSuccess: () => {
              refresh();
              refreshCount();
            },
          }
        )
      }
      disabled={isPending}
      className="h-9 px-3 text-xs"
    >
      Все прочитаны
    </PillButton>
  );
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
  const [wsConnected, setWsConnected] = useState(false);
  const [activeTab, setActiveTab] = useState<Tab>('unread');
  const { data, isLoading } = useListContext<Notification>();
  const records = data ?? [];

  const unread = records.filter((n) => !n.read);
  const read = records.filter((n) => n.read);
  const visible = activeTab === 'unread' ? unread : read;

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;

    const wsUrl = `${WS_BASE}/ws/live?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => setWsConnected(true);
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'ADMIN_NOTIFICATION') refresh();
      } catch {
        // ignore
      }
    };
    ws.onclose = () => setWsConnected(false);

    return () => ws.close();
  }, [refresh]);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center text-[#74777f]">
        <span className="animate-pulse">Загрузка...</span>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6">
      <div className="mb-4 flex items-center justify-between lg:mb-6">
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-bold text-[#1a1c1e]">Уведомления</h1>
          <span
            className={`h-2 w-2 rounded-full ${wsConnected ? 'bg-[#34a853]' : 'bg-[#ea4335]'}`}
            title={wsConnected ? 'Подключено' : 'Отключено'}
          />
        </div>
        <div className="flex items-center gap-2">
          <PillButton
            variant="secondary"
            icon={<IconRefresh size={16} />}
            onClick={refresh}
            className="h-9 px-3 text-xs"
          >
            Обновить
          </PillButton>
          {activeTab === 'unread' && <MarkAllAsReadButton />}
        </div>
      </div>

      <div className="mb-4 flex items-center gap-2 lg:mb-6">
        <TabButton active={activeTab === 'unread'} onClick={() => setActiveTab('unread')}>
          Непрочитанные ({unread.length})
        </TabButton>
        <TabButton active={activeTab === 'read'} onClick={() => setActiveTab('read')}>
          Прочитанные ({read.length})
        </TabButton>
      </div>

      <AdminCard>
        {visible.length === 0 ? (
          <div className="flex h-64 items-center justify-center text-[#74777f]">
            {activeTab === 'unread'
              ? 'Нет непрочитанных уведомлений'
              : 'Нет прочитанных уведомлений'}
          </div>
        ) : (
          <>
            <MobileCardList
              records={visible}
              renderCard={(note) => (
                <div className="rounded-[20px] bg-white p-4">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-sm font-semibold text-[#1a1c1e]">
                      {typeLabel(note.type)}
                    </span>
                    <StatusPill variant={severityVariant(note.severity)}>
                      {note.severity}
                    </StatusPill>
                  </div>
                  <div className="mb-1 text-sm text-[#1a1c1e]">{note.message}</div>
                  <div className="mb-3 text-xs text-[#74777f]">
                    {note.instanceId} {note.deviceCode ? `· ${note.deviceCode}` : ''} ·{' '}
                    {new Date(note.createdAt).toLocaleString('ru-RU')}
                  </div>
                  <NotificationActions note={note} />
                </div>
              )}
            />
            <DesktopDataTable
              records={visible}
              keyExtractor={(note) => note.id}
              columns={[
                {
                  key: 'type',
                  header: 'Тип',
                  render: (note) => typeLabel(note.type),
                },
                {
                  key: 'severity',
                  header: 'Важность',
                  render: (note) => (
                    <StatusPill variant={severityVariant(note.severity)}>
                      {note.severity}
                    </StatusPill>
                  ),
                },
                { key: 'instance', header: 'Автомат', render: (note) => note.instanceId },
                {
                  key: 'device',
                  header: 'Устройство',
                  render: (note) => note.deviceCode ?? '—',
                },
                { key: 'message', header: 'Сообщение', render: (note) => note.message },
                {
                  key: 'time',
                  header: 'Время',
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
      </AdminCard>
    </div>
  );
}
