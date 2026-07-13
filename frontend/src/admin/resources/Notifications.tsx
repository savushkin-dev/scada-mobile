import { useListContext, useRefresh, useUpdate } from 'react-admin';
import { useEffect, useState } from 'react';
import { WS_BASE } from '../../config';
import { getAccessToken } from '../../auth/session';
import { AdminCard } from '../ui/AdminCard';

import { PillButton } from '../ui/PillButton';
import { StatusPill } from '../ui/StatusPill';
import { MobileCardList } from '../ui/MobileCardList';
import { DesktopDataTable } from '../ui/DesktopDataTable';
import { IconRefresh, IconCheck } from '../ui/icons';

interface Notification {
  id: number | string;
  type: string;
  severity: string;
  instanceId: string;
  deviceCode?: string;
  message: string;
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
    default:
      return type;
  }
}

function MarkAllAsReadButton() {
  const [update, { isPending }] = useUpdate();
  const refresh = useRefresh();

  return (
    <PillButton
      variant="secondary"
      icon={<IconCheck size={16} />}
      onClick={() =>
        update('notifications', { id: 'read-all', data: {} }, { onSuccess: () => refresh() })
      }
      disabled={isPending}
      className="h-9 px-3 text-xs"
    >
      Все прочитаны
    </PillButton>
  );
}

function MarkAsReadButton({ id }: { id: number | string }) {
  const [update, { isPending }] = useUpdate();
  const refresh = useRefresh();

  return (
    <PillButton
      variant="secondary"
      icon={<IconCheck size={16} />}
      onClick={() => update('notifications', { id, data: {} }, { onSuccess: () => refresh() })}
      disabled={isPending}
      className="h-9 px-3 text-xs"
    >
      Прочитано
    </PillButton>
  );
}

export function NotificationList() {
  const refresh = useRefresh();
  const [wsConnected, setWsConnected] = useState(false);
  const { data, isLoading } = useListContext<Notification>();
  const records = data ?? [];

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
          <MarkAllAsReadButton />
        </div>
      </div>

      <AdminCard>
        {records.length === 0 ? (
          <div className="flex h-64 items-center justify-center text-[#74777f]">
            Нет уведомлений
          </div>
        ) : (
          <>
            <MobileCardList
              records={records}
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
                  <MarkAsReadButton id={note.id} />
                </div>
              )}
            />
            <DesktopDataTable
              records={records}
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
                  render: (note) => <MarkAsReadButton id={note.id} />,
                },
              ]}
            />
          </>
        )}
      </AdminCard>
    </div>
  );
}
