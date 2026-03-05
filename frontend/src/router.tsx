/**
 * Дерево маршрутов приложения (React Router v6).
 *
 * Структура URL:
 *   /                                        → DashboardPage  (список цехов)
 *   /workshops/:workshopId                   → WorkshopPage   (список аппаратов)
 *   /workshops/:workshopId/units/:unitId     → DetailsPage    (детали + табы)
 *
 * Табы на DetailsPage хранятся в search-параметре «?tab=»,
 * что делает каждый таб deep-linkable и сохраняет URL при обновлении страницы.
 *
 * Все страницы завёрнуты в единый RootLayout, который содержит:
 *   - AppProvider   (глобальный стейт: алёрты, топология, live-статус)
 *   - useLiveWs     (единственное WebSocket-соединение приложения)
 *   - touchmove-блокировку pull-to-refresh на мобильных
 */

import { createBrowserRouter, Navigate } from 'react-router-dom';
import { RootLayout } from './layouts/RootLayout';
import { DashboardPage } from './pages/DashboardPage';
import { WorkshopPage } from './pages/WorkshopPage';
import { DetailsPage } from './pages/DetailsPage';

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
        element: <DetailsPage />,
      },
      {
        // Любой неизвестный URL → главная страница
        path: '*',
        element: <Navigate to="/" replace />,
      },
    ],
  },
]);
