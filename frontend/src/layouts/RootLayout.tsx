import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Outlet, useLocation, useMatch } from 'react-router-dom';
import { ALERT_VIBRATION_COOLDOWN_MS, ALERT_VIBRATION_PATTERN } from '../config';
import { AppProvider, useAppContext } from '../context/AppContext';
import { useAuth } from '../context/AuthContext';
import { useAccessControl } from '../context/AccessControlContext';
import { useUserProfile } from '../context/UserProfileContext';
import { PageHeaderProvider, usePageHeaderContext } from '../context/PageHeaderContext';
import { PageHeader } from '../components/PageHeader';
import { useLiveWs } from '../hooks/useLiveWs';
import { useHardwareBackGuard } from '../hooks/useHardwareBackGuard';
import { refreshAccessToken } from '../api/auth';
import { pushNotificationEvent, syncNotificationSnapshot } from '../lib/notificationSwBridge';
import type {
  AlertWsMessage,
  DeviceChangedMessage,
  EmployeeChangedMessage,
  NotificationWsMessage,
  UnitsStatusMessage,
  UnitChangedMessage,
  UserAssignmentsMessage,
  UserNotificationSettingsChangedMessage,
  WorkshopChangedMessage,
} from '../types';

type AlertRouteScope =
  | { kind: 'dashboard' }
  | { kind: 'workshop'; workshopId: number }
  | { kind: 'unit'; unitId: string }
  | { kind: 'other' };

function resolveAlertRouteScope(pathname: string): AlertRouteScope {
  const segments = pathname.split('/').filter(Boolean);
  if (segments.length === 0) return { kind: 'dashboard' };
  if (segments[0] !== 'workshops') return { kind: 'other' };
  if (segments.length === 2) return { kind: 'workshop', workshopId: Number(segments[1]) || 0 };
  if (segments.length >= 4 && segments[2] === 'units') {
    return { kind: 'unit', unitId: segments[3] };
  }
  return { kind: 'other' };
}

function shouldVibrateAlert(msg: AlertWsMessage, scope: AlertRouteScope): boolean {
  // Вибрация только на появление активной ошибки.
  if (!msg.active || msg.errors.length === 0) return false;

  switch (scope.kind) {
    case 'unit':
      return scope.unitId === String(msg.unitId);
    case 'workshop':
      return scope.workshopId === msg.workshopId;
    case 'dashboard':
      return true;
    default:
      return false;
  }
}

/**
 * Внутренний компонент, имеющий доступ к AppContext и PageHeaderContext.
 *
 * Содержит:
 *  - единственный экземпляр `<PageHeader />`, управляемый через контекст;
 *  - глобальное WS-соединение (useLiveWs);
 *  - блокировку pull-to-refresh на мобильных.
 *
 * Шапка не пересоздаётся при смене маршрута — она всегда присутствует
 * в DOM, а её содержимое обновляется из активной страницы через хук
 * {@link usePageHeader}.
 */
function RootLayoutInner() {
  const {
    handleAlert,
    patchUnitsStatus,
    setAlertSnapshot,
    handleNotification,
    setNotificationSnapshot,
    setHeaderError,
    clearHeaderError,
    setSignalState,
    applyWorkshopChange,
    applyUnitChange,
    invalidateDevicesTopology,
    bumpTopologyVersion,
  } = useAppContext();
  const { userId, role, logout, updateRole } = useAuth();
  const { updateAssignedUnitsFromWs } = useAccessControl();
  const {
    applyOwnEmployeeChange,
    applyAssignmentsFromWs,
    applyUnitTopologyChange,
    applyOwnSettingsChange,
  } = useUserProfile();

  const { config } = usePageHeaderContext();
  const location = useLocation();
  const alertRouteScope = useMemo(
    () => resolveAlertRouteScope(location.pathname),
    [location.pathname]
  );
  const lastAlertVibrationAtRef = useRef(0);

  // Подписываемся на UNITS_STATUS только когда открыт экран цеха (/workshops/:workshopId).
  // На странице деталей аппарата (/units/:unitId) используется отдельный useUnitWs.
  // Это воспроизводит оригинальное поведение: WS-подписка только на WorkshopPage.
  const workshopExact = useMatch('/workshops/:workshopId');
  const subscribedWorkshopId = workshopExact?.params.workshopId
    ? Number(workshopExact.params.workshopId) || null
    : null;

  const handleAlertSnapshot = useCallback(
    (alerts: AlertWsMessage[]) => setAlertSnapshot(alerts),
    [setAlertSnapshot]
  );

  const handleNotificationSnapshot = useCallback(
    (notifications: NotificationWsMessage[]) => {
      setNotificationSnapshot(notifications);
      void syncNotificationSnapshot(notifications);
    },
    [setNotificationSnapshot]
  );

  const handleUnitsStatus = useCallback(
    (msg: UnitsStatusMessage) => patchUnitsStatus(msg.workshopId, msg.payload),
    [patchUnitsStatus]
  );

  const handleLiveAlert = useCallback(
    (msg: AlertWsMessage) => {
      handleAlert(msg);

      if (document.visibilityState !== 'visible') return;
      if (!shouldVibrateAlert(msg, alertRouteScope)) return;
      if (typeof navigator.vibrate !== 'function') return;

      const now = Date.now();
      if (now - lastAlertVibrationAtRef.current < ALERT_VIBRATION_COOLDOWN_MS) return;

      lastAlertVibrationAtRef.current = now;
      navigator.vibrate(ALERT_VIBRATION_PATTERN);
    },
    [handleAlert, alertRouteScope]
  );

  const handleLiveNotification = useCallback(
    (msg: NotificationWsMessage) => {
      handleNotification(msg);
      void pushNotificationEvent(msg);
    },
    [handleNotification]
  );

  const handleUserAssignments = useCallback(
    (msg: UserAssignmentsMessage) => {
      updateAssignedUnitsFromWs(msg);
      applyAssignmentsFromWs(msg);
    },
    [updateAssignedUnitsFromWs, applyAssignmentsFromWs]
  );

  const handleForceLogout = useCallback(() => {
    logout();
  }, [logout]);

  const handleEmployeeChanged = useCallback(
    (msg: EmployeeChangedMessage) => {
      const payload = msg.payload;
      if (payload == null) return;
      if (String(payload.id) !== userId) return;
      // Учётку удалили или деактивировали — разлогиниваем сразу,
      // не дожидаясь истечения access-токена.
      if (msg.action === 'DELETE' || !payload.active) {
        logout();
        return;
      }
      // Админ изменил данные текущего пользователя — патчим профиль и роль
      // сразу из события, без REST и без перезагрузки страницы.
      applyOwnEmployeeChange(payload);
      if (payload.roleName != null && payload.roleName !== role) {
        updateRole(payload.roleName);
        // Claim role в access token устарел — перевыпускаем токен сразу,
        // иначе API будет проверять старую роль до планового рефреша.
        void refreshAccessToken();
      }
    },
    [userId, role, applyOwnEmployeeChange, updateRole, logout]
  );

  const handleWorkshopChanged = useCallback(
    (msg: WorkshopChangedMessage) => {
      if (msg.payload) {
        applyWorkshopChange(msg.payload, msg.action);
      }
      bumpTopologyVersion();
    },
    [applyWorkshopChange, bumpTopologyVersion]
  );

  const handleUnitChanged = useCallback(
    (msg: UnitChangedMessage) => {
      if (msg.payload) {
        applyUnitChange(msg.payload, msg.action);
        applyUnitTopologyChange(msg.payload, msg.action);
      }
      bumpTopologyVersion();
    },
    [applyUnitChange, applyUnitTopologyChange, bumpTopologyVersion]
  );

  const handleDeviceChanged = useCallback(
    (msg: DeviceChangedMessage) => {
      const instanceId = msg.payload?.printsrvInstanceId;
      if (instanceId) {
        invalidateDevicesTopology(instanceId);
      }
      bumpTopologyVersion();
    },
    [invalidateDevicesTopology, bumpTopologyVersion]
  );

  const handleRoleChanged = useCallback(() => {
    // Изменения справочника ролей затрагивают только админку;
    // роль текущего пользователя обновляется через EMPLOYEE_CHANGED.
  }, []);

  const handleDeviceCatalogChanged = useCallback(() => {
    bumpTopologyVersion();
  }, [bumpTopologyVersion]);

  const handleDeviceTypeChanged = useCallback(() => {
    bumpTopologyVersion();
  }, [bumpTopologyVersion]);

  const handleUserNotificationSettingsChanged = useCallback(
    (msg: UserNotificationSettingsChangedMessage) => {
      const payload = msg.payload;
      if (payload?.userId != null && String(payload.userId) === userId) {
        applyOwnSettingsChange(payload, msg.action);
      }
    },
    [userId, applyOwnSettingsChange]
  );

  // Перехватывает события popstate (кнопка «назад» на Android / в браузере)
  // и гарантирует навигацию строго по иерархии экранов приложения.
  useHardwareBackGuard();

  // Единственное WebSocket-соединение для всего приложения:
  // ALERT_SNAPSHOT при подключении, NOTIFICATION_SNAPSHOT, UNITS_STATUS для цеха, ALERT и NOTIFICATION-дельты.
  // Соединение живёт всю сессию и не обрывается при смене страниц.
  useLiveWs(subscribedWorkshopId, userId, {
    onAlertSnapshot: handleAlertSnapshot,
    onNotificationSnapshot: handleNotificationSnapshot,
    onUnitsStatus: handleUnitsStatus,
    onAlert: handleLiveAlert,
    onNotification: handleLiveNotification,
    onUserAssignments: handleUserAssignments,
    onForceLogout: handleForceLogout,
    onEmployeeChanged: handleEmployeeChanged,
    onWorkshopChanged: handleWorkshopChanged,
    onRoleChanged: handleRoleChanged,
    onUnitChanged: handleUnitChanged,
    onDeviceChanged: handleDeviceChanged,
    onDeviceCatalogChanged: handleDeviceCatalogChanged,
    onDeviceTypeChanged: handleDeviceTypeChanged,
    onUserNotificationSettingsChanged: handleUserNotificationSettingsChanged,
    onReconnecting: () => {
      setSignalState('live', 'reconnecting');
    },
    onError: (error) => {
      setSignalState('live', 'error');
      setHeaderError('live', error);
    },
    onRecovered: () => {
      setSignalState('live', 'connected');
      clearHeaderError('live');
    },
  });

  useEffect(() => {
    // Блокируем pull-to-refresh на мобильных устройствах.
    // Прокручиваемые области используют атрибут data-scroll и не блокируются.
    const handler = (e: TouchEvent) => {
      if ((e.target as HTMLElement).closest('[data-scroll]') == null) {
        e.preventDefault();
      }
    };
    document.body.addEventListener('touchmove', handler, { passive: false });
    return () => document.body.removeEventListener('touchmove', handler);
  }, []);

  return (
    <>
      <PageHeader title={config.title} subtitle={config.subtitle} variant={config.variant} />
      <Outlet />
    </>
  );
}

/**
 * Корневой layout-компонент приложения.
 *
 * Оборачивает всё дерево маршрутов в AppProvider + PageHeaderProvider
 * и монтирует глобальные side-эффекты (WebSocket, touch-блокировка).
 *
 * Архитектурно: AppProvider → PageHeaderProvider → RootLayoutInner → <Outlet />
 * Единственный экземпляр PageHeader рендерится здесь, а дочерние маршруты
 * управляют его содержимым через хук usePageHeader.
 */
export function RootLayout() {
  return (
    <AppProvider>
      <PageHeaderProvider>
        <RootLayoutInner />
      </PageHeaderProvider>
    </AppProvider>
  );
}
