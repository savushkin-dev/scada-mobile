import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import { getAccessToken } from '../auth/session';
import { isTokenExpired } from '../auth/token';
import { refreshAccessToken } from '../api/auth';
import { createManagedWs } from '../lib/createManagedWs';
import { LiveWsIncomingMessageSchema } from '../schemas';

/**
 * Типы админ-сущностей, чьи изменения приходят по /ws/live.
 */
export type AdminEntityType =
  | 'employee'
  | 'workshop'
  | 'role'
  | 'unit'
  | 'device'
  | 'device-catalog'
  | 'device-type'
  | 'user-notification-settings';

/**
 * Window-событие о новом админ-уведомлении (ADMIN_NOTIFICATION по WS).
 * Слушает AdminNotificationsContext для обновления счётчика непрочитанных.
 */
export const ADMIN_NOTIFICATION_EVENT = 'scada:admin-notification-received';

const TYPE_TO_ENTITY: Partial<Record<string, AdminEntityType>> = {
  EMPLOYEE_CHANGED: 'employee',
  WORKSHOP_CHANGED: 'workshop',
  ROLE_CHANGED: 'role',
  UNIT_CHANGED: 'unit',
  DEVICE_CHANGED: 'device',
  DEVICE_CATALOG_CHANGED: 'device-catalog',
  DEVICE_TYPE_CHANGED: 'device-type',
  USER_NOTIFICATION_SETTINGS_CHANGED: 'user-notification-settings',
};

export interface AdminLiveWsCallbacks {
  /** Изменилась админ-сущность (CREATE/UPDATE/DELETE) — надо инвалидировать кэш. */
  onEntityChanged: (entity: AdminEntityType, id?: string) => void;
  /** Пришло новое админ-уведомление. */
  onAdminNotification: () => void;
  /** Сервер принудительно разлогинил этого пользователя. */
  onForceLogout: () => void;
}

/**
 * WebSocket-подписка админ-панели на единый канал {@code /ws/live}.
 *
 * Мониторинговая часть приложения монтирует свой useLiveWs только на
 * не-админских маршрутах, поэтому админка держит собственное подключение
 * к тому же каналу (одновременно активно всегда только одно из двух).
 * Реконнект с backoff и обновление JWT перед подключением — как у useLiveWs.
 */
export function useAdminLiveWs(callbacks: AdminLiveWsCallbacks): void {
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;

    const conn = createManagedWs({
      url: () => {
        const currentToken = getAccessToken();
        return `${WS_BASE}/ws/live?token=${encodeURIComponent(currentToken ?? token)}`;
      },
      source: 'ws/live',
      onBeforeConnect: async () => {
        const currentToken = getAccessToken();
        if (currentToken && isTokenExpired(currentToken)) {
          const newToken = await refreshAccessToken();
          if (!newToken) {
            throw new Error('Token refresh failed');
          }
        }
      },
      onMessage: (e) => {
        let raw: unknown;
        try {
          raw = JSON.parse(e.data as string);
        } catch {
          return;
        }

        const result = LiveWsIncomingMessageSchema.safeParse(raw);
        if (!result.success) return;

        const msg = result.data;
        if (msg.type === 'ADMIN_NOTIFICATION') {
          callbacksRef.current.onAdminNotification();
          return;
        }
        if (msg.type === 'FORCE_LOGOUT') {
          callbacksRef.current.onForceLogout();
          return;
        }

        const entity = TYPE_TO_ENTITY[msg.type];
        if (!entity) return;

        const payload = 'payload' in msg ? msg.payload : null;
        const id =
          payload != null && !Array.isArray(payload) && 'id' in payload ? payload.id : undefined;
        callbacksRef.current.onEntityChanged(entity, id != null ? String(id) : undefined);
      },
    });

    return () => conn.destroy();
  }, []);
}
