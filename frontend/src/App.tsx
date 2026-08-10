import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { AccessControlProvider } from './context/AccessControlContext';
import { AuthProvider } from './context/AuthContext';
import { UserProfileProvider } from './context/UserProfileContext';

/**
 * Корневой компонент приложения.
 * Вся навигация, layout и глобальные side-эффекты делегированы
 * в router.tsx (маршруты) и layouts/RootLayout.tsx (AppProvider + WS).
 */
export default function App() {
  return (
    <AuthProvider>
      <UserProfileProvider>
        <AccessControlProvider>
          <RouterProvider router={router} />
        </AccessControlProvider>
      </UserProfileProvider>
    </AuthProvider>
  );
}
