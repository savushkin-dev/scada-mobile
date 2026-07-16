import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import { getAccessToken } from '../auth/session';
import { isTokenExpired } from '../auth/token';
import { refreshAccessToken } from '../api/auth';
import { classifyError } from '../errors/classifyError';
import type { AppError } from '../errors/AppError';
import { createManagedWs, type ManagedWsConnection } from '../lib/createManagedWs';
import { LiveWsIncomingMessageSchema } from '../schemas';
import type {
  AlertWsMessage,
  DeviceCatalogChangedMessage,
  DeviceChangedMessage,
  DeviceTypeChangedMessage,
  EmployeeChangedMessage,
  ForceLogoutMessage,
  NotificationWsMessage,
  RoleChangedMessage,
  UnitsStatusMessage,
  UnitChangedMessage,
  UserAssignmentsMessage,
  UserNotificationSettingsChangedMessage,
  WorkshopChangedMessage,
} from '../types';

/**
 * Коллбэки, вызываемые хуком при получении сообщений от сервера.
 * Все ссылки стабилизируются через ref — хук не переподключается при смене функций.
 */
export interface LiveWsCallbacks {
  /** ALERT_SNAPSHOT — начальный срез активных алёртов при подключении */
  onAlertSnapshot: (alerts: AlertWsMessage[]) => void;
  /** NOTIFICATION_SNAPSHOT — начальный срез активных уведомлений при подключении */
  onNotificationSnapshot: (notifications: NotificationWsMessage[]) => void;
  /** UNITS_STATUS — live-статус аппаратов подписанного цеха */
  onUnitsStatus: (msg: UnitsStatusMessage) => void;
  /** ALERT — дельта изменения ошибки (active true/false) */
  onAlert: (msg: AlertWsMessage) => void;
  /** NOTIFICATION — дельта изменения уведомления (active true/false) */
  onNotification: (msg: NotificationWsMessage) => void;
  /** USER_ASSIGNMENTS — актуальный список автоматов, закреплённых за пользователем */
  onUserAssignments?: (msg: UserAssignmentsMessage) => void;
  /** EMPLOYEE_CHANGED — изменились данные сотрудника */
  onEmployeeChanged?: (msg: EmployeeChangedMessage) => void;
  /** WORKSHOP_CHANGED — изменились данные цеха */
  onWorkshopChanged?: (msg: WorkshopChangedMessage) => void;
  /** ROLE_CHANGED — изменилась роль */
  onRoleChanged?: (msg: RoleChangedMessage) => void;
  /** UNIT_CHANGED — изменились данные автомата */
  onUnitChanged?: (msg: UnitChangedMessage) => void;
  /** DEVICE_CHANGED — изменилась связь устройства с автоматом */
  onDeviceChanged?: (msg: DeviceChangedMessage) => void;
  /** DEVICE_CATALOG_CHANGED — изменился справочник устройств */
  onDeviceCatalogChanged?: (msg: DeviceCatalogChangedMessage) => void;
  /** DEVICE_TYPE_CHANGED — изменился тип устройства */
  onDeviceTypeChanged?: (msg: DeviceTypeChangedMessage) => void;
  /** USER_NOTIFICATION_SETTINGS_CHANGED — изменились настройки уведомлений */
  onUserNotificationSettingsChanged?: (msg: UserNotificationSettingsChangedMessage) => void;
  /** FORCE_LOGOUT — принудительный разлогин */
  onForceLogout?: (msg: ForceLogoutMessage) => void;
  /**
   * Первый разрыв соединения — начало тихого переподключения.
   * UI должен показывать skeleton, но не ошибку в шапке.
   */
  onReconnecting?: () => void;
  /** Транспортная или parse-ошибка канала /ws/live — исчерпан порог попыток. */
  onError?: (error: AppError) => void;
  /** Соединение восстановлено: socket физически открыт или пришло валидное сообщение. */
  onRecovered?: () => void;
}

/**
 * Единственный WebSocket-хук приложения. Открывает одно постоянное соединение
 * к {@code /ws/live} и мультиплексирует все live-данные через него.
 *
 * <h3>Соединение</h3>
 * Использует {@link createManagedWs} — фабрику с единой стратегией reconnect:
 * exponential backoff + jitter, корректный teardown на unmount.
 *
 * <h3>Протокол</h3>
 * <ul>
 *   <li>При подключении сервер немедленно присылает {@code ALERT_SNAPSHOT}.</li>
 *   <li>Хук подписывается / отписывается от цеха, отправляя JSON-действия серверу.</li>
 *   <li>При реконнекте подписка на текущий цех автоматически восстанавливается
 *       через коллбэк {@code onOpen} фабрики.</li>
 * </ul>
 *
 * <h3>Жизненный цикл</h3>
 * Монтируется один раз на уровне {@code AppInner} — живёт всё время работы приложения.
 * Соединение не закрывается при смене экранов — только меняется подписка на цех.
 *
 * @param subscribedWorkshopId ID цеха для подписки (SUBSCRIBE_WORKSHOP/UNSUBSCRIBE_WORKSHOP),
 *                             или {@code null} когда пользователь не на экране цеха.
 * @param userId                Идентификатор пользователя (для триггера переподключения).
 * @param callbacks            Коллбэки для трёх типов входящих сообщений.
 */
export function useLiveWs(
  subscribedWorkshopId: number | null,
  userId: string | null,
  callbacks: LiveWsCallbacks
): void {
  // Ссылка на управляемое соединение — нужна второму эффекту для отправки сообщений
  const connRef = useRef<ManagedWsConnection | null>(null);

  // Stable refs: хук не пересоздаёт соединение при изменении коллбэков / workshop
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;

  const subscribedWorkshopIdRef = useRef(subscribedWorkshopId);
  subscribedWorkshopIdRef.current = subscribedWorkshopId;

  // Effect 1: открываем соединение при наличии токена
  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      if (connRef.current) {
        connRef.current.destroy();
        connRef.current = null;
      }
      return;
    }

    const conn = createManagedWs({
      url: () => {
        const currentToken = getAccessToken();
        return `${WS_BASE}/ws/live?token=${encodeURIComponent(currentToken ?? token)}`;
      },
      source: 'ws/live',
      onReconnecting: () => callbacksRef.current.onReconnecting?.(),
      onError: (error) => callbacksRef.current.onError?.(error),
      onRecovered: () => callbacksRef.current.onRecovered?.(),

      // onBeforeConnect вызывается перед каждой попыткой подключения.
      // Позволяет обновить токен, если он истёк, избегая infinite reconnect loop.
      onBeforeConnect: async () => {
        const currentToken = getAccessToken();
        if (currentToken && isTokenExpired(currentToken)) {
          const newToken = await refreshAccessToken();
          if (!newToken) {
            // Refresh не удался — прерываем попытку подключения
            throw new Error('Token refresh failed');
          }
        }
      },

      // onOpen вызывается при каждом успешном подключении (в т.ч. после реконнекта).
      // Восстанавливаем подписку на цех, если она была активна в момент обрыва.
      onOpen: (ws) => {
        const workshopId = subscribedWorkshopIdRef.current;
        if (workshopId) {
          ws.send(JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId }));
        }
      },

      onMessage: (e) => {
        let raw: unknown;
        try {
          raw = JSON.parse(e.data as string);
        } catch (error) {
          callbacksRef.current.onError?.(classifyError(error, 'ws/live'));
          return;
        }

        const result = LiveWsIncomingMessageSchema.safeParse(raw);
        if (!result.success) {
          // forward compat: неизвестный type или структурная ошибка — молча пропускаем.
          // В dev-режиме логируем для отладки.
          if (import.meta.env.DEV) {
            console.warn('[ws/live] неожиданное сообщение от сервера', result.error.issues);
          }
          return;
        }

        const msg = result.data;
        const cb = callbacksRef.current;
        cb.onRecovered?.();
        switch (msg.type) {
          case 'ALERT_SNAPSHOT':
            cb.onAlertSnapshot(msg.payload);
            break;
          case 'NOTIFICATION_SNAPSHOT':
            cb.onNotificationSnapshot(msg.payload);
            break;
          case 'UNITS_STATUS':
            cb.onUnitsStatus(msg);
            break;
          case 'ALERT':
            cb.onAlert(msg);
            break;
          case 'NOTIFICATION':
            cb.onNotification(msg);
            break;
          case 'USER_ASSIGNMENTS':
            cb.onUserAssignments?.(msg);
            break;
          case 'EMPLOYEE_CHANGED':
            cb.onEmployeeChanged?.(msg);
            break;
          case 'WORKSHOP_CHANGED':
            cb.onWorkshopChanged?.(msg);
            break;
          case 'ROLE_CHANGED':
            cb.onRoleChanged?.(msg);
            break;
          case 'UNIT_CHANGED':
            cb.onUnitChanged?.(msg);
            break;
          case 'DEVICE_CHANGED':
            cb.onDeviceChanged?.(msg);
            break;
          case 'DEVICE_CATALOG_CHANGED':
            cb.onDeviceCatalogChanged?.(msg);
            break;
          case 'DEVICE_TYPE_CHANGED':
            cb.onDeviceTypeChanged?.(msg);
            break;
          case 'USER_NOTIFICATION_SETTINGS_CHANGED':
            cb.onUserNotificationSettingsChanged?.(msg);
            break;
          case 'FORCE_LOGOUT':
            cb.onForceLogout?.(msg);
            break;
        }
      },
    });

    connRef.current = conn;

    return () => {
      connRef.current = null;
      conn.destroy();
    };
  }, [userId]);

  // Effect 2: управляем подпиской на цех при изменении subscribedWorkshopId.
  // conn.send() — noop если соединение ещё не открыто;
  // в этом случае onOpen в Effect 1 восстановит подписку при открытии.
  useEffect(() => {
    const conn = connRef.current;
    if (conn === null) return;

    if (subscribedWorkshopId) {
      // SUBSCRIBE_WORKSHOP атомарно заменяет предыдущую подписку на сервере
      conn.send(JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId: subscribedWorkshopId }));
    } else {
      conn.send(JSON.stringify({ action: 'UNSUBSCRIBE_WORKSHOP' }));
    }
  }, [subscribedWorkshopId]);
}
