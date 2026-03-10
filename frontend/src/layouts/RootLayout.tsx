import { useCallback, useEffect } from 'react';
import { Outlet, useMatch } from 'react-router-dom';
import { AppProvider, useAppContext } from '../context/AppContext';
import { PageHeaderProvider, usePageHeaderContext } from '../context/PageHeaderContext';
import { PageHeader } from '../components/PageHeader';
import { useLiveWs } from '../hooks/useLiveWs';
import type { AlertWsMessage, UnitsStatusMessage } from '../types';

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
    setHeaderError,
    clearHeaderError,
    setSignalState,
  } = useAppContext();

  const { config } = usePageHeaderContext();

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

  return (
    <>
      <PageHeader
        title={config.title}
        subtitle={config.subtitle}
        variant={config.variant}
        onBack={config.onBack}
      />
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
