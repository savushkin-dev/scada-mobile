import {
  Datagrid,
  List,
  TextField,
  DateField,
  FunctionField,
  useRefresh,
  Button,
  TopToolbar,
  useUpdate,
} from 'react-admin';
import { useEffect, useState } from 'react';
import { WS_BASE } from '../../config';
import { getAccessToken } from '../../auth/session';

/**
 * Панель системных уведомлений администратора.
 *
 * Показывает непрочитанные уведомления от бэкенда
 * (DEVICE_DISCOVERED, DEVICE_DISCONNECTED, DEVICE_RECONNECTED).
 * Обновляется по WebSocket в реальном времени.
 */
function MarkAllAsReadButton() {
  const [update, { isPending }] = useUpdate();
  const refresh = useRefresh();

  return (
    <Button
      label="Отметить все прочитанными"
      disabled={isPending}
      onClick={() => {
        update(
          'notifications',
          { id: 'read-all', data: {} },
          {
            onSuccess: () => refresh(),
          }
        );
      }}
    />
  );
}

export function NotificationListActions() {
  const refresh = useRefresh();
  return (
    <TopToolbar>
      <Button label="Обновить" onClick={refresh} />
      <MarkAllAsReadButton />
    </TopToolbar>
  );
}

function severityColor(severity: string) {
  switch (severity) {
    case 'WARNING':
      return 'text-red-600 font-semibold';
    case 'INFO':
    default:
      return 'text-blue-600';
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

function MarkAsReadButton({ id }: { id: number | string }) {
  const [update, { isPending }] = useUpdate();
  const refresh = useRefresh();

  return (
    <Button
      label="Прочитано"
      disabled={isPending}
      onClick={() => {
        update(
          'notifications',
          { id, data: {} },
          {
            onSuccess: () => refresh(),
          }
        );
      }}
    />
  );
}

export function NotificationList() {
  const refresh = useRefresh();
  const [wsConnected, setWsConnected] = useState(false);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;

    const wsUrl = `${WS_BASE}/ws/live?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      setWsConnected(true);
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'ADMIN_NOTIFICATION') {
          // Пришло новое уведомление — обновляем список
          refresh();
        }
      } catch {
        // ignore malformed messages
      }
    };

    ws.onclose = () => {
      setWsConnected(false);
    };

    return () => {
      ws.close();
    };
  }, [refresh]);

  return (
    <List actions={<NotificationListActions />} title="Уведомления администратора">
      <Datagrid rowClick={false} bulkActionButtons={false}>
        <FunctionField
          source="type"
          label="Тип"
          render={(record: { type: string }) => typeLabel(record.type)}
        />
        <FunctionField
          source="severity"
          label="Важность"
          render={(record: { severity: string }) => (
            <span className={severityColor(record.severity)}>{record.severity}</span>
          )}
        />
        <TextField source="instanceId" label="Автомат" />
        <TextField source="deviceCode" label="Устройство" emptyText="—" />
        <TextField source="message" label="Сообщение" />
        <DateField source="createdAt" label="Время" showTime />
        <FunctionField
          label="Действие"
          render={(record: { id: number | string }) => <MarkAsReadButton id={record.id} />}
        />
        <FunctionField
          label="WS"
          render={() => (
            <span
              className={`inline-block h-2 w-2 rounded-full ${
                wsConnected ? 'bg-green-500' : 'bg-red-500'
              }`}
              title={wsConnected ? 'Подключено' : 'Отключено'}
            />
          )}
        />
      </Datagrid>
    </List>
  );
}
