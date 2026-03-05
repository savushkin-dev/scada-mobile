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
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js').catch((err) => {
      console.error('[SW] Registration failed:', err);
    });
  });
}
