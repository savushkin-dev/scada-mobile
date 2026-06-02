import { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { PAGE_FADE_SECTION_STYLE } from '../config';
import { useAppContext } from '../context/AppContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { NotificationCard } from '../components/NotificationCard';
import { SkeletonBlock } from '../components/skeleton/SkeletonBlock';

const NOTIFICATIONS_COPY = Object.freeze({
  title: 'Уведомления',
  empty: 'Нет активных уведомлений',
});

/** Служебные страницы, которые пропускаются при навигации назад. */
const TRANSIENT_ROUTES = ['/profile', '/notifications', '/login'];

function isTransientRoute(pathname: string): boolean {
  return TRANSIENT_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

/**
 * Возвращает ближайшую неслужебную страницу из истории.
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

function NotificationsSkeleton() {
  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-4" aria-hidden="true">
      {Array.from({ length: 3 }, (_, i) => (
        <div
          key={i}
          className="card p-4"
          style={{
            backgroundColor: '#FFFBEB',
            borderColor: '#F59E0B',
          }}
        >
          <div className="mb-2">
            <SkeletonBlock height="18px" width="55%" borderRadius="6px" />
          </div>
          <div className="space-y-1.5">
            <SkeletonBlock height="10px" width="30%" borderRadius="4px" />
            <SkeletonBlock height="14px" width="65%" borderRadius="4px" />
            <SkeletonBlock height="10px" width="25%" borderRadius="4px" />
            <SkeletonBlock height="14px" width="50%" borderRadius="4px" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Страница активных производственных уведомлений.
 *
 * Показывает список всех активных уведомлений от работников
 * в виде плиточных карточек жёлтого (warning) цвета.
 */
export function NotificationsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { state } = useAppContext();

  const handleBack = useCallback(() => {
    const target = findNonTransientBackTarget(location.state, '/');
    navigate(target, { replace: true });
  }, [navigate, location.state]);

  usePageHeader(NOTIFICATIONS_COPY.title, undefined, 'default', handleBack);

  const notifications = Array.from(state.notifications.entries()).map(([unitId, data]) => ({
    unitId,
    ...data,
  }));

  const isLoading = state.signalStates.live === 'reconnecting' && notifications.length === 0;

  return (
    <section data-scroll style={PAGE_FADE_SECTION_STYLE}>
      <main className="px-5 pb-12 pt-6 sm:px-7">
        <div className="mx-auto flex w-full max-w-[520px] flex-col gap-4">
          {isLoading ? (
            <NotificationsSkeleton />
          ) : notifications.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-16">
              <p className="text-center text-sm font-medium text-[#74777F]">
                {NOTIFICATIONS_COPY.empty}
              </p>
            </div>
          ) : (
            notifications.map((n) => <NotificationCard key={n.unitId} notification={n} />)
          )}
        </div>
      </main>
    </section>
  );
}
