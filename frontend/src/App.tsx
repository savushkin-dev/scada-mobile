import { useEffect } from 'react';
import { AppProvider, useAppContext } from './context/AppContext';
import { useAlertsWs } from './hooks/useAlertsWs';
import { DashboardPage } from './pages/DashboardPage';
import { WorkshopPage } from './pages/WorkshopPage';
import { DetailsPage } from './pages/DetailsPage';

function AppInner() {
  const { state, handleAlert } = useAppContext();
  useAlertsWs(handleAlert);

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
