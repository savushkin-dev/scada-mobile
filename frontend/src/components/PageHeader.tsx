import { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { UI_COPY } from '../config';
import { useAuth } from '../context/AuthContext';
import { useAppContext } from '../context/AppContext';
import { HeaderErrorIndicator } from './HeaderErrorIndicator';

/**
 * Единственная шапка приложения.
 *
 * Рендерится один раз в {@link RootLayout}, содержимое управляется
 * из страниц через хук `usePageHeader()`.
 *
 * Визуально шапка всегда закреплена в верхней части экрана (flex-shrink: 0)
 * и не прокручивается вместе с контентом.
 *
 * Кнопки «назад» в шапке нет осознанно: возврат выполняется физической
 * кнопкой терминала (см. useHardwareBackGuard), а на экране 4.2" каждый
 * элемент шапки отнимает место у контента.
 *
 * Иконки уведомлений/профиля работают как toggle: повторный тап при
 * открытой соответствующей странице закрывает её и возвращает на экран,
 * с которого она была открыта.
 */

/** Служебные страницы, которые пропускаются при закрытии служебного экрана. */
const TRANSIENT_ROUTES = ['/profile', '/notifications', '/tasks', '/login'];

function isTransientRoute(pathname: string): boolean {
  return TRANSIENT_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

/**
 * Возвращает ближайшую неслужебную страницу из location.state.from.
 * Если такой нет — возвращает fallback.
 */
function findNonTransientBackTarget(locationState: unknown, fallback: string): string {
  const state = locationState as { from?: { pathname?: string } } | null;
  const fromPath = state?.from?.pathname;
  if (fromPath && !isTransientRoute(fromPath)) {
    return fromPath;
  }
  return fallback;
}

interface PageHeaderProps {
  /** Основной заголовок страницы */
  title: string;
  /** Маленький надзаголовок над title (необязателен) */
  subtitle?: string;
  /** Компактный режим для вложенных/детальных экранов. */
  variant?: 'default' | 'compact';
}

export function PageHeader({ title, subtitle }: PageHeaderProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, userId } = useAuth();
  const { state } = useAppContext();
  const headerClassName =
    'z-10 h-[60px] backdrop-blur-md bg-[#f8f9fa]/30 border-b border-white/15 flex items-center gap-3 flex-shrink-0 px-4 py-2 sm:px-6 lg:px-8';

  const titleClassName = 'text-xl font-bold text-[#1A1C1E] leading-tight truncate';

  const isProfileRoute = location.pathname.startsWith('/profile');
  const isNotificationsRoute = location.pathname.startsWith('/notifications');

  // Бейдж считает только уведомления, реально видимые текущему пользователю
  // (та же фильтрация, что в NotificationsPage): PENDING по подпискам плюс
  // свои созданные/принятые IN_PROGRESS. Принятые другими — не считаем.
  const activeNotificationCount = Array.from(state.notifications.values()).filter(
    (n) =>
      (n.status ?? 'PENDING') === 'PENDING' || n.creatorId === userId || n.acceptedBy === userId
  ).length;

  const handleProfileClick = useCallback(() => {
    if (isProfileRoute) {
      // Повторный тап закрывает страницу профиля — назад к экрану-источнику.
      navigate(findNonTransientBackTarget(location.state, '/'), { replace: true });
      return;
    }
    const target = isAuthenticated ? '/profile' : '/login';
    navigate(target, { state: { from: location } });
  }, [navigate, location, isAuthenticated, isProfileRoute]);

  const handleNotificationClick = useCallback(() => {
    if (isNotificationsRoute) {
      // Повторный тап закрывает страницу уведомлений — назад к экрану-источнику.
      navigate(findNonTransientBackTarget(location.state, '/'), { replace: true });
      return;
    }
    navigate('/notifications', { state: { from: location } });
  }, [navigate, location, isNotificationsRoute]);

  return (
    <header className={headerClassName}>
      <div className="flex min-w-0 flex-1 items-center gap-3 overflow-hidden">
        <div className="min-w-0 overflow-hidden flex flex-col justify-center">
          {subtitle ? (
            <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase">
              {subtitle}
            </p>
          ) : null}
          <h1 className={titleClassName}>{title}</h1>
        </div>
      </div>
      {isAuthenticated && (
        <div className="ml-auto flex items-center gap-3">
          <HeaderErrorIndicator />
          <button
            type="button"
            onClick={handleNotificationClick}
            aria-label={UI_COPY.notificationButtonAriaLabel}
            aria-pressed={isNotificationsRoute}
            className={
              'relative flex h-10 w-10 items-center justify-center rounded-full backdrop-blur-sm transition-all duration-200 ease-in-out active:scale-[0.98] ' +
              (isNotificationsRoute
                ? 'border border-[#2b2f36] bg-[#2b2f36] text-white shadow-[0_0_10px_rgba(17,24,39,0.18)]'
                : 'border-0 bg-transparent text-[#1A1C1E] shadow-none')
            }
          >
            <img
              src="/assets/bell.svg"
              alt=""
              aria-hidden="true"
              className={
                'h-5 w-5 transition-all duration-200 ease-in-out ' +
                (isNotificationsRoute ? 'invert' : 'invert-0')
              }
            />
            {activeNotificationCount > 0 && (
              <span
                className="absolute -top-0.5 -right-0.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-[#F59E0B] px-1 text-[9px] font-bold text-white shadow-sm"
                aria-hidden="true"
              >
                {activeNotificationCount > 99 ? '99+' : activeNotificationCount}
              </span>
            )}
          </button>
          <button
            type="button"
            onClick={handleProfileClick}
            aria-label={UI_COPY.profileButtonAriaLabel}
            aria-pressed={isProfileRoute}
            className={
              'flex h-10 w-10 items-center justify-center rounded-full backdrop-blur-sm transition-all duration-200 ease-in-out active:scale-[0.98] ' +
              (isProfileRoute
                ? 'border border-[#2b2f36] bg-[#2b2f36] text-white shadow-[0_0_10px_rgba(17,24,39,0.18)]'
                : 'border-0 bg-transparent text-[#1A1C1E] shadow-none')
            }
          >
            <img
              src="/assets/user-tie.svg"
              alt=""
              aria-hidden="true"
              className={
                'h-5 w-5 transition-all duration-200 ease-in-out ' +
                (isProfileRoute ? 'invert' : 'invert-0')
              }
            />
          </button>
        </div>
      )}
    </header>
  );
}
