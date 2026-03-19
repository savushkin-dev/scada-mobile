/**
 * main.tsx - web client entrypoint.
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';
import { ErrorBoundary } from './errors/ErrorBoundary';
import { bindViewportCssVars } from './lib/viewport';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Root element not found');

bindViewportCssVars();

createRoot(rootElement).render(
  <StrictMode>
    {/*
      ErrorBoundary — последний рубеж перехвата рендер-крашей.
      Аналог Spring BasicErrorController: если ошибка не перехвачена на уровне
      конкретного компонента, она попадает сюда вместо пустого экрана.
      Классификация попадает в classifyError (source: 'ui/render').
    */}
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>
);

if ('serviceWorker' in navigator) {
  if (import.meta.env.DEV) {
    void navigator.serviceWorker.getRegistrations().then((registrations) => {
      registrations.forEach((registration) => {
        void registration.unregister();
      });
    });
  } else {
    const SW_URL = `/service-worker.js?v=${encodeURIComponent(__BUILD_ID__)}`;

    const activateWaitingWorker = (registration: ServiceWorkerRegistration) => {
      registration.waiting?.postMessage({ type: 'SKIP_WAITING' });
    };

    let hasReloadedForSwUpdate = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (hasReloadedForSwUpdate) return;
      hasReloadedForSwUpdate = true;
      window.location.reload();
    });

    window.addEventListener('load', () => {
      navigator.serviceWorker
        .register(SW_URL)
        .then((registration) => {
          activateWaitingWorker(registration);

          registration.addEventListener('updatefound', () => {
            const worker = registration.installing;
            if (!worker) return;

            worker.addEventListener('statechange', () => {
              if (worker.state === 'installed' && navigator.serviceWorker.controller) {
                activateWaitingWorker(registration);
              }
            });
          });
        })
        .catch((err) => {
          console.error('[SW] Registration failed:', err);
        });
    });
  }
}
