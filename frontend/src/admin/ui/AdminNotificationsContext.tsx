/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react';
import { API_BASE } from '../../config';
import { apiFetchJson } from '../../api/client';
import { getAccessToken } from '../../auth/session';
import { ADMIN_NOTIFICATION_EVENT } from '../useAdminLiveWs';

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

    // Новые админ-уведомления приходят по /ws/live — AdminLiveUpdater
    // рассылает ADMIN_NOTIFICATION_EVENT; WS-соединение здесь не нужно.
    const handler = () => {
      void refreshCount();
    };
    window.addEventListener(ADMIN_NOTIFICATION_EVENT, handler);

    // Фолбэк: раз в 30 секунд сверяемся с сервером
    const interval = setInterval(refreshCount, 30_000);

    return () => {
      window.removeEventListener(ADMIN_NOTIFICATION_EVENT, handler);
      clearInterval(interval);
    };
  }, [refreshCount]);

  return (
    <AdminNotificationsContext.Provider value={{ unreadCount, refreshCount }}>
      {children}
    </AdminNotificationsContext.Provider>
  );
}
