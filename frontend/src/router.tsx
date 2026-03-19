/**
 * Дерево маршрутов приложения (React Router v6).
 *
 * Структура URL:
 *   /                                                → DashboardPage  (список цехов)
 *   /workshops/:workshopId                           → WorkshopPage   (список аппаратов)
 *   /workshops/:workshopId/units/:unitId             → redirect → /batch
 *   /workshops/:workshopId/units/:unitId/batch       → BatchTab
 *   /workshops/:workshopId/units/:unitId/devices     → DevicesTab
 *   /workshops/:workshopId/units/:unitId/queue       → QueueTab
 *   /workshops/:workshopId/units/:unitId/logs        → LogsTab
 *
 * Архитектурно:
 *   - RootLayout содержит единственный экземпляр PageHeader;
 *     каждая страница декларативно задаёт содержимое шапки через usePageHeader().
 *   - DetailsLayout содержит единственный BottomNav + Fab;
 *     вкладки деталей — вложенные маршруты, рендерятся через <Outlet />.
 *   - Данные для вкладок доступны через DetailsContext (WS + REST).
 */

import { lazy, Suspense, type ReactElement } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { RootLayout } from './layouts/RootLayout';

const DetailsLayout = lazy(async () => {
  const module = await import('./layouts/DetailsLayout');
  return { default: module.DetailsLayout };
});

const DashboardPage = lazy(async () => {
  const module = await import('./pages/DashboardPage');
  return { default: module.DashboardPage };
});

const WorkshopPage = lazy(async () => {
  const module = await import('./pages/WorkshopPage');
  return { default: module.WorkshopPage };
});

const BatchTab = lazy(async () => {
  const module = await import('./components/details/BatchTab');
  return { default: module.BatchTab };
});

const DevicesTab = lazy(async () => {
  const module = await import('./components/details/DevicesTab');
  return { default: module.DevicesTab };
});

const QueueTab = lazy(async () => {
  const module = await import('./components/details/QueueTab');
  return { default: module.QueueTab };
});

const LogsTab = lazy(async () => {
  const module = await import('./components/details/LogsTab');
  return { default: module.LogsTab };
});

const routeFallback = (
  <section className="px-4 py-6 text-center text-[#74777F] text-sm sm:px-6 lg:px-8">
    Loading...
  </section>
);

function withSuspense(element: ReactElement): ReactElement {
  return <Suspense fallback={routeFallback}>{element}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        index: true,
        element: withSuspense(<DashboardPage />),
      },
      {
        path: 'workshops/:workshopId',
        element: withSuspense(<WorkshopPage />),
      },
      {
        path: 'workshops/:workshopId/units/:unitId',
        element: withSuspense(<DetailsLayout />),
        children: [
          {
            index: true,
            element: <Navigate to="batch" replace />,
          },
          {
            path: 'batch',
            element: withSuspense(<BatchTab />),
          },
          {
            path: 'devices',
            element: withSuspense(<DevicesTab />),
          },
          {
            path: 'queue',
            element: withSuspense(<QueueTab />),
          },
          {
            path: 'logs',
            element: withSuspense(<LogsTab />),
          },
        ],
      },
      {
        // Любой неизвестный URL → главная страница
        path: '*',
        element: <Navigate to="/" replace />,
      },
    ],
  },
]);
