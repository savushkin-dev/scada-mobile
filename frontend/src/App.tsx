import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { AuthProvider } from './context/AuthContext';

/**
 * Корневой компонент приложения.
 * Вся навигация, layout и глобальные side-эффекты делегированы
 * в router.tsx (маршруты) и layouts/RootLayout.tsx (AppProvider + WS).
 */
export default function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
