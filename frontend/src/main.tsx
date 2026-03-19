/**
 * main.tsx — входная точка web-клиента.
 *
 * Ответственность файла:
 * - инициализировать React-приложение в DOM-узле `#root`;
 * - подключить глобальный перехват рендер-ошибок через {@link ErrorBoundary};
 * - зарегистрировать service worker (PWA/TWA канал).
 *
 * Архитектурный поток приложения описан в {@link ./router.tsx} и
 * {@link ./layouts/RootLayout.tsx}.
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';
import { ErrorBoundary } from './errors/ErrorBoundary';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Root element not found');

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
    // В dev отключаем SW, чтобы не держать старый кэш/бандл при отладке через ngrok.
    void navigator.serviceWorker.getRegistrations().then((registrations) => {
      registrations.forEach((registration) => {
        void registration.unregister();
      });
    });
  } else {
    // SW регистрируем после полной загрузки страницы, чтобы не блокировать first paint.
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/service-worker.js').catch((err) => {
        console.error('[SW] Registration failed:', err);
      });
    });
  }
}
