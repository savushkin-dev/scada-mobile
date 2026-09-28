import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { PAGE_FADE_SECTION_STYLE, UI_PALETTE } from '../config';
import { useAppContext } from '../context/AppContext';
import { useAuth } from '../context/AuthContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { NotificationCard } from '../components/NotificationCard';
import { SkeletonBlock } from '../components/skeleton/SkeletonBlock';
import {
  fetchSentHistory,
  updateNotification,
  type NotificationAction,
  type NotificationHistoryStatus,
  type NotificationWorkflowEntry,
} from '../api/notifications';
import { getServerErrorMessage } from '../api/client';
import type { NotificationData } from '../types';

const PAGE_SIZE = 20;

const NOTIFICATIONS_COPY = Object.freeze({
  title: 'Уведомления',
  empty: 'Нет активных уведомлений',
  myTasks: 'Мои задачи',
  history: 'История отправленных',
  historyEmpty: 'Вы ещё не отправляли уведомления',
  actionError: 'Не удалось изменить статус уведомления',
  historyError: 'Не удалось загрузить историю',
  completedFilter: 'Выполненные',
  cancelledFilter: 'Отменённые',
});

function NotificationsSkeleton() {
  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3" aria-hidden="true">
      {Array.from({ length: 3 }, (_, i) => (
        <div
          key={i}
          className="card p-4"
          style={{
            backgroundColor: UI_PALETTE.warningBg,
            borderColor: UI_PALETTE.warning,
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

/** Маппинг REST-записи истории во внутреннюю модель карточки уведомления. */
function toNotificationData(
  entry: NotificationWorkflowEntry
): NotificationData & { unitId: string } {
  return {
    unitId: entry.unitId,
    unitName: entry.unitName ?? entry.unitId,
    creatorId: entry.creatorId,
    creatorName: entry.creatorName,
    timestamp: entry.activatedAt,
    eventType: null,
    notificationId: entry.notificationId,
    status: entry.status,
    acceptedBy: entry.acceptedBy,
    acceptedByName: entry.acceptedByName,
    acceptedAt: entry.acceptedAt,
    completedAt: entry.completedAt,
    cancelledAt: entry.cancelledAt,
    version: entry.version,
  };
}

function selectedStatusesToArray(
  filters: Record<NotificationHistoryStatus, boolean>
): NotificationHistoryStatus[] {
  const result: NotificationHistoryStatus[] = [];
  if (filters.COMPLETED) result.push('COMPLETED');
  if (filters.CANCELLED) result.push('CANCELLED');
  return result;
}

/**
 * Страница производственных уведомлений.
 *
 * Активный список (live, WebSocket): PENDING-уведомления по подпискам
 * плюс свои созданные/принятые IN_PROGRESS. Ниже — история отправленных
 * текущим пользователем (REST sent-history, терминальные статусы)
 * с динамической подгрузкой по скроллу.
 */
export function NotificationsPage() {
  const { state } = useAppContext();
  const { userId } = useAuth();
  const [pendingAction, setPendingAction] = useState<{
    id: number;
    action: NotificationAction;
  } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [history, setHistory] = useState<NotificationWorkflowEntry[] | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyFilters, setHistoryFilters] = useState<Record<NotificationHistoryStatus, boolean>>({
    COMPLETED: true,
    CANCELLED: true,
  });
  const [historyPage, setHistoryPage] = useState(0);
  const [hasMoreHistory, setHasMoreHistory] = useState(true);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const historySentinelRef = useRef<HTMLDivElement | null>(null);
  const location = useLocation();
  const isTasksRoute = location.pathname === '/notifications/tasks';

  usePageHeader(NOTIFICATIONS_COPY.title, undefined, 'default');

  const loadHistory = useCallback(
    async (page: number, filters: Record<NotificationHistoryStatus, boolean>, append: boolean) => {
      const statuses = selectedStatusesToArray(filters);
      if (statuses.length === 0) {
        setHistory([]);
        setHasMoreHistory(false);
        return;
      }

      setIsLoadingHistory(true);
      setHistoryError(null);
      try {
        const next = await fetchSentHistory(statuses, page, PAGE_SIZE);
        setHistory((prev) => (append && prev != null ? [...prev, ...next] : next));
        setHistoryPage(page);
        setHasMoreHistory(next.length === PAGE_SIZE);
      } catch {
        setHistoryError(NOTIFICATIONS_COPY.historyError);
      } finally {
        setIsLoadingHistory(false);
      }
    },
    []
  );

  // Первая загрузка истории + сброс при смене фильтра.
  useEffect(() => {
    setHistory(null);
    setHasMoreHistory(true);
    void loadHistory(0, historyFilters, false);
  }, [loadHistory, historyFilters]);

  // При live-изменениях уведомлений перезагружаем первую страницу истории,
  // чтобы новые терминальные записи появились сразу.
  useEffect(() => {
    if (history == null) return;
    void loadHistory(0, historyFilters, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.notifications]);

  // Infinite scroll: подгружаем следующую страницу, когда sentinel попадает в viewport.
  useEffect(() => {
    const sentinel = historySentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry.isIntersecting && hasMoreHistory && !isLoadingHistory) {
          void loadHistory(historyPage + 1, historyFilters, true);
        }
      },
      { rootMargin: '100px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMoreHistory, isLoadingHistory, historyPage, historyFilters, loadHistory]);

  const notifications = Array.from(state.notifications.entries())
    .map(([unitId, data]) => ({ unitId, ...data }))
    // FR-3: принятое в работу уведомление видят только создатель и исполнитель.
    .filter(
      (n) =>
        (n.status ?? 'PENDING') === 'PENDING' || n.creatorId === userId || n.acceptedBy === userId
    )
    .sort((a, b) => (b.timestamp ?? '').localeCompare(a.timestamp ?? ''));

  const myTasksCount = Array.from(state.notifications.values()).filter(
    (n) => n.status === 'IN_PROGRESS' && n.acceptedBy === userId
  ).length;

  const isLoading = state.signalStates.live === 'reconnecting' && notifications.length === 0;

  const handleAction = async (notificationId: number, action: NotificationAction) => {
    setPendingAction({ id: notificationId, action });
    setActionError(null);
    try {
      await updateNotification(notificationId, action);
      if (action === 'accept') {
        navigator.vibrate?.(50);
      }
      void loadHistory(0, historyFilters, false);
    } catch (error) {
      setActionError(getServerErrorMessage(error, NOTIFICATIONS_COPY.actionError));
    } finally {
      setPendingAction(null);
    }
  };

  const toggleHistoryFilter = (status: NotificationHistoryStatus) => {
    setHistoryFilters((prev) => {
      const currentlyActive = Object.values(prev).filter(Boolean).length;
      // Всегда должен быть включён хотя бы один фильтр.
      if (prev[status] && currentlyActive <= 1) {
        return prev;
      }
      return { ...prev, [status]: !prev[status] };
    });
  };

  const showHistorySection = history != null || historyError || isLoadingHistory;

  if (isTasksRoute) {
    return <Outlet />;
  }

  return (
    <section data-scroll style={PAGE_FADE_SECTION_STYLE}>
      <main className="px-4 pb-6 pt-4 sm:px-6">
        <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3">
          <Link
            to="/notifications/tasks"
            className="card flex items-center gap-3 p-4 no-underline"
            style={{ borderColor: '#3B82F6' }}
          >
            <img
              src="/assets/check-circle.svg"
              alt=""
              aria-hidden="true"
              className="h-6 w-6"
              style={{
                filter:
                  'brightness(0) saturate(100%) invert(48%) sepia(79%) saturate(2476%) hue-rotate(207deg) brightness(98%) contrast(96%)',
              }}
            />
            <span className="flex-1 text-sm font-bold text-[#1A1C1E]">
              {NOTIFICATIONS_COPY.myTasks}
            </span>
            {myTasksCount > 0 && (
              <span className="inline-flex min-w-6 items-center justify-center rounded-full bg-[#3B82F6] px-2 py-0.5 text-xs font-bold text-white">
                {myTasksCount}
              </span>
            )}
          </Link>

          {actionError && (
            <p
              className="rounded-2xl p-3 text-sm font-medium"
              style={{
                backgroundColor: UI_PALETTE.retryBackground,
                border: `1px solid ${UI_PALETTE.retryBorder}`,
                color: UI_PALETTE.retryText,
              }}
              role="alert"
            >
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
                  key={n.notificationId ?? n.unitId}
                  notification={n}
                  currentUserId={userId}
                  onAction={handleAction}
                  pendingAction={currentAction}
                />
              );
            })
          )}

          {showHistorySection && (
            <>
              <h2 className="mt-4 text-[0.72rem] font-semibold uppercase tracking-wide text-[#74777F]">
                {NOTIFICATIONS_COPY.history}
              </h2>

              <div className="flex flex-wrap gap-2">
                {(['COMPLETED', 'CANCELLED'] as const).map((status) => {
                  const active = historyFilters[status];
                  return (
                    <button
                      key={status}
                      type="button"
                      onClick={() => toggleHistoryFilter(status)}
                      className="rounded-full px-3 py-1 text-xs font-semibold transition"
                      style={{
                        backgroundColor: active ? '#3B82F6' : '#EDF0F4',
                        color: active ? '#FFFFFF' : '#74777F',
                      }}
                    >
                      {status === 'COMPLETED'
                        ? NOTIFICATIONS_COPY.completedFilter
                        : NOTIFICATIONS_COPY.cancelledFilter}
                    </button>
                  );
                })}
              </div>
            </>
          )}

          {historyError && (
            <p
              className="rounded-2xl p-3 text-sm font-medium"
              style={{
                backgroundColor: UI_PALETTE.retryBackground,
                border: `1px solid ${UI_PALETTE.retryBorder}`,
                color: UI_PALETTE.retryText,
              }}
              role="alert"
            >
              {historyError}
            </p>
          )}

          {history == null && !historyError ? (
            <div className="card p-4" aria-hidden="true">
              <div className="space-y-1.5">
                <SkeletonBlock height="16px" width="50%" borderRadius="6px" />
                <SkeletonBlock height="12px" width="70%" borderRadius="4px" />
              </div>
            </div>
          ) : (
            (() => {
              const displayHistory = history ?? [];
              return (
                <>
                  {displayHistory.length === 0 ? (
                    <p className="py-8 text-center text-sm font-medium text-[#74777F]">
                      {NOTIFICATIONS_COPY.historyEmpty}
                    </p>
                  ) : (
                    displayHistory.map((entry) => (
                      <NotificationCard
                        key={entry.notificationId}
                        notification={toNotificationData(entry)}
                        currentUserId={userId}
                      />
                    ))
                  )}
                  <div ref={historySentinelRef} className="h-4" aria-hidden="true" />
                  {isLoadingHistory && (
                    <div className="py-2 text-center text-xs text-[#74777F]">Загрузка…</div>
                  )}
                </>
              );
            })()
          )}
        </div>
      </main>
    </section>
  );
}
