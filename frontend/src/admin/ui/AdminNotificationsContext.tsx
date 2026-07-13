/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react';
import { API_BASE, WS_BASE } from '../../config';
import { apiFetchJson } from '../../api/client';
import { getAccessToken } from '../../auth/session';

interface AdminNotificationsContextValue {
  unreadCount: number;
  refreshCount: () => void;
}

const AdminNotificationsContext = createContext<AdminNotificationsContextValue | null>(null);

export function useAdminNotificationsCount(): AdminNotificationsContextValue {
  const ctx = useContext(AdminNotificationsContext);
  if (!ctx) {
    throw new Error('useAdminNotificationsCount must be used within AdminNotificationsProvider');
  }
  return ctx;
}

export function AdminNotificationsProvider({ children }: { children: ReactNode }) {
  const [unreadCount, setUnreadCount] = useState(0);

  const refreshCount = useCallback(async () => {
    const token = getAccessToken();
    if (!token) return;
    try {
      const data = (await apiFetchJson(`${API_BASE}/api/v1.0.0/admin/notifications/count`)) as {
        count: number;
      };
      setUnreadCount(data.count ?? 0);
    } catch (e) {
      // Не ломаем UI при временных сетевых проблемах
      console.warn('[admin-notifications] Failed to fetch unread count', e);
    }
  }, []);

  useEffect(() => {
    refreshCount();

    const token = getAccessToken();
    if (!token) return;

    const wsUrl = `${WS_BASE}/ws/live?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(wsUrl);

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'ADMIN_NOTIFICATION') {
          refreshCount();
        }
      } catch {
        // ignore
      }
    };

    // Фолбэк: раз в 30 секунд сверяемся с сервером
    const interval = setInterval(refreshCount, 30_000);

    return () => {
      ws.close();
      clearInterval(interval);
    };
  }, [refreshCount]);

  return (
    <AdminNotificationsContext.Provider value={{ unreadCount, refreshCount }}>
      {children}
    </AdminNotificationsContext.Provider>
  );
}
