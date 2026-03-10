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

import { createBrowserRouter, Navigate } from 'react-router-dom';
import { RootLayout } from './layouts/RootLayout';
import { DetailsLayout } from './layouts/DetailsLayout';
import { DashboardPage } from './pages/DashboardPage';
import { WorkshopPage } from './pages/WorkshopPage';
import { BatchTab } from './components/details/BatchTab';
import { DevicesTab } from './components/details/DevicesTab';
import { QueueTab } from './components/details/QueueTab';
import { LogsTab } from './components/details/LogsTab';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        index: true,
        element: <DashboardPage />,
      },
      {
        path: 'workshops/:workshopId',
        element: <WorkshopPage />,
      },
      {
        path: 'workshops/:workshopId/units/:unitId',
        element: <DetailsLayout />,
        children: [
          {
            index: true,
            element: <Navigate to="batch" replace />,
          },
          {
            path: 'batch',
            element: <BatchTab />,
          },
          {
            path: 'devices',
            element: <DevicesTab />,
          },
          {
            path: 'queue',
            element: <QueueTab />,
          },
          {
            path: 'logs',
            element: <LogsTab />,
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
