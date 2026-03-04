import { useEffect, useCallback } from 'react';
import { AppProvider, useAppContext } from './context/AppContext';
import { useAlertsWs } from './hooks/useAlertsWs';
import { useWorkshopsStatusWs } from './hooks/useWorkshopsStatusWs';
import { DashboardPage } from './pages/DashboardPage';
import { WorkshopPage } from './pages/WorkshopPage';
import { DetailsPage } from './pages/DetailsPage';
import type { WorkshopsStatusMessage } from './types';

function AppInner() {
  const { state, handleAlert, patchWorkshopsStatus } = useAppContext();

  useAlertsWs(handleAlert);

  // Подписываемся на live-статус цехов один раз на уровне App.
  // Хук живёт всё время работы приложения — данные обновляются непрерывно.
  const handleWorkshopsStatus = useCallback(
    (msg: WorkshopsStatusMessage) => patchWorkshopsStatus(msg.payload),
    [patchWorkshopsStatus]
  );
  useWorkshopsStatusWs(handleWorkshopsStatus);

  return (
    <>
      {state.screen === 'dashboard' && <DashboardPage />}
      {state.screen === 'workshop' && <WorkshopPage />}
      {state.screen === 'details' && <DetailsPage />}
    </>
  );
}

export default function App() {
  useEffect(() => {
    // Prevent pull-to-refresh on mobile
    document.body.addEventListener(
      'touchmove',
      (e) => {
        if ((e.target as HTMLElement).closest('[data-scroll]') == null) {
          e.preventDefault();
        }
      },
      { passive: false }
    );
  }, []);

  return (
    <AppProvider>
      <AppInner />
    </AppProvider>
  );
}
