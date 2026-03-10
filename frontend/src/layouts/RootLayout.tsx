import { useCallback, useEffect } from 'react';
import { Outlet, useMatch } from 'react-router-dom';
import { AppProvider, useAppContext } from '../context/AppContext';
import { useLiveWs } from '../hooks/useLiveWs';
import type { AlertWsMessage, UnitsStatusMessage } from '../types';

/**
 * Внутренний компонент, имеющий доступ к AppContext.
 * Живёт внутри AppProvider — именно здесь размещается вся логика,
 * которая должна работать на протяжении всей сессии приложения.
 */
function RootLayoutInner() {
  const {
    handleAlert,
    patchUnitsStatus,
    setAlertSnapshot,
    setHeaderError,
    clearHeaderError,
    setSignalState,
  } = useAppContext();

  // Подписываемся на UNITS_STATUS только когда открыт экран цеха (/workshops/:workshopId).
  // На странице деталей аппарата (/units/:unitId) используется отдельный useUnitWs.
  // Это воспроизводит оригинальное поведение: WS-подписка только на WorkshopPage.
  const workshopExact = useMatch('/workshops/:workshopId');
  const subscribedWorkshopId = workshopExact?.params.workshopId ?? null;

  const handleAlertSnapshot = useCallback(
    (alerts: AlertWsMessage[]) => setAlertSnapshot(alerts),
    [setAlertSnapshot]
  );

  const handleUnitsStatus = useCallback(
    (msg: UnitsStatusMessage) => patchUnitsStatus(msg.workshopId, msg.payload),
    [patchUnitsStatus]
  );

  // Единственное WebSocket-соединение для всего приложения:
  // ALERT_SNAPSHOT при подключении, UNITS_STATUS для цеха, ALERT-дельты глобально.
  // Соединение живёт всю сессию и не обрывается при смене страниц.
  //
  // Логика переходов signalState для канала 'live':
  //   idle / connected → первый разрыв   → 'reconnecting' (skeleton, без ошибки)
  //   'reconnecting'  → N-я удачная   → 'connected'    (данные пошли)
  //   'reconnecting'  → 5-й разрыв    → 'error'        (ошибка + заголовок)
  //   'error'         → восстановление   → 'connected'    (убираем ошибку)
  useLiveWs(subscribedWorkshopId, {
    onAlertSnapshot: handleAlertSnapshot,
    onUnitsStatus: handleUnitsStatus,
    onAlert: handleAlert,
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

  return <Outlet />;
}

/**
 * Корневой layout-компонент приложения.
 *
 * Оборачивает всё дерево маршрутов в AppProvider и монтирует
 * глобальные side-эффекты (WebSocket, touch-блокировка).
 *
 * Архитектурно: AppProvider → RootLayoutInner → <Outlet />
 * (дочерние маршруты рендерятся через Outlet).
 */
export function RootLayout() {
  return (
    <AppProvider>
      <RootLayoutInner />
    </AppProvider>
  );
}
