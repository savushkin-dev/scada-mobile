import { PAGE_FADE_SECTION_STYLE } from '../config';
import { useAppContext } from '../context/AppContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { NotificationCard } from '../components/NotificationCard';
import { SkeletonBlock } from '../components/skeleton/SkeletonBlock';
import { updateNotification, type NotificationAction } from '../api/notifications';
import { useState } from 'react';

const NOTIFICATIONS_COPY = Object.freeze({
  title: 'Уведомления',
  empty: 'Нет активных уведомлений',
});

function NotificationsSkeleton() {
  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3" aria-hidden="true">
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
  const { state } = useAppContext();
  const [pendingAction, setPendingAction] = useState<{
    id: number;
    action: NotificationAction;
  } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  usePageHeader(NOTIFICATIONS_COPY.title, undefined, 'default');

  const notifications = Array.from(state.notifications.entries()).map(([unitId, data]) => ({
    unitId,
    ...data,
  }));

  const isLoading = state.signalStates.live === 'reconnecting' && notifications.length === 0;

  const handleAction = async (notificationId: number, action: NotificationAction) => {
    setPendingAction({ id: notificationId, action });
    setActionError(null);
    try {
      await updateNotification(notificationId, action);
      if (action === 'accept') {
        navigator.vibrate?.(50);
      }
    } catch {
      setActionError('Не удалось изменить статус уведомления');
    } finally {
      setPendingAction(null);
    }
  };

  return (
    <section data-scroll style={PAGE_FADE_SECTION_STYLE}>
      <main className="px-4 pb-6 pt-4 sm:px-6">
        <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3">
          {actionError && (
            <p className="rounded-lg bg-red-50 p-3 text-sm font-medium text-red-700" role="alert">
              {actionError}
            </p>
          )}
          {isLoading ? (
            <NotificationsSkeleton />
          ) : notifications.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-16">
              <p className="text-center text-sm font-medium text-[#74777F]">
                {NOTIFICATIONS_COPY.empty}
              </p>
            </div>
          ) : (
            notifications.map((n) => {
              const currentAction =
                pendingAction && pendingAction.id === n.notificationId
                  ? pendingAction.action
                  : null;
              return (
                <NotificationCard
                  key={n.unitId}
                  notification={n}
                  onAction={handleAction}
                  pendingAction={currentAction}
                />
              );
            })
          )}
        </div>
      </main>
    </section>
  );
}
