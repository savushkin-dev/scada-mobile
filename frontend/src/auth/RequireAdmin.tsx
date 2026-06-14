import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Route guard: защищает админ-маршруты, требуя аутентификации и роли ADMIN.
 *
 * Пока идёт начальная проверка токена (isVerifying) — показывает минимальный
 * загрузчик, чтобы избежать "authenticated flash" при старте с expired token.
 */
export function RequireAdmin() {
  const { isAuthenticated, isAdmin, isVerifying } = useAuth();

  if (isVerifying) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#e7e8ea] border-t-[#1A1C1E]" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
