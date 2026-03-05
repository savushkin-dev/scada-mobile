import { RouterProvider } from 'react-router-dom';
import { router } from './router';

/**
 * Корневой компонент приложения.
 * Вся навигация, layout и глобальные side-эффекты делегированы
 * в router.tsx (маршруты) и layouts/RootLayout.tsx (AppProvider + WS).
 */
export default function App() {
  return <RouterProvider router={router} />;
}
