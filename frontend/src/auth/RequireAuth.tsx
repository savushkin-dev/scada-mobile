import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getAccessToken } from '../auth/session';
import { isTemporaryPasswordToken } from '../auth/token';

/**
 * Route guard: защищает вложенные маршруты, требуя аутентификации.
 *
 * Пока идёт начальная проверка токена (isVerifying) — показывает минимальный
 * загрузчик, чтобы избежать "authenticated flash" при старте с expired token.
 *
 * Если пользователь вошёл с временным паролем, он может находиться только
 * на странице смены пароля — все остальные маршруты редиректят туда.
 */
export function RequireAuth() {
  const { isAuthenticated, isVerifying } = useAuth();
  const location = useLocation();

  if (isVerifying) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#e7e8ea] border-t-[#1A1C1E]" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const isTemporaryPassword = isTemporaryPasswordToken(getAccessToken());
  if (isTemporaryPassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
