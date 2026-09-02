import { useCallback, useEffect, useRef, useState } from 'react';
import { PAGE_FADE_SECTION_STYLE, UI_PALETTE } from '../config';
import { useAppContext } from '../context/AppContext';
import { useAuth } from '../context/AuthContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { NotificationCard } from '../components/NotificationCard';
import { SkeletonBlock } from '../components/skeleton/SkeletonBlock';
import {
  fetchExecutorHistory,
  updateNotification,
  type NotificationAction,
  type NotificationWorkflowEntry,
} from '../api/notifications';
import { getServerErrorMessage } from '../api/client';
import type { NotificationData } from '../types';

const PAGE_SIZE = 20;

const MY_TASKS_COPY = Object.freeze({
  title: 'Мои задачи',
  empty: 'Нет задач в работе',
  history: 'Завершённые',
  historyEmpty: 'Нет завершённых задач',
  actionError: 'Не удалось изменить статус уведомления',
  historyError: 'Не удалось загрузить завершённые задачи',
});

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

/**
 * Раздел «Мои задачи» оператора паллетайзера.
 *
 * Верхний список — задачи в работе: live-данные из WebSocket-состояния
 * (уведомления IN_PROGRESS, принятые текущим пользователем), кнопка «Готово»
 * завершает задачу, карточка исчезает по WS-событию без перезагрузки.
 * Ниже — завершённые задачи из REST executor-history с динамической подгрузкой.
 */
export function MyTasksPage() {
  usePageHeader(MY_TASKS_COPY.title, undefined, 'default');
  const { state } = useAppContext();
  const { userId } = useAuth();
  const [pendingAction, setPendingAction] = useState<{
    id: number;
    action: NotificationAction;
  } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [history, setHistory] = useState<NotificationWorkflowEntry[] | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [hasMoreHistory, setHasMoreHistory] = useState(true);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const historySentinelRef = useRef<HTMLDivElement | null>(null);

  const tasks = Array.from(state.notifications.entries())
    .map(([unitId, data]) => ({ unitId, ...data }))
    .filter((n) => n.status === 'IN_PROGRESS' && n.acceptedBy === userId)
    .sort((a, b) => (a.acceptedAt ?? '').localeCompare(b.acceptedAt ?? ''));

  const loadHistory = useCallback(async (page: number, append: boolean) => {
    setIsLoadingHistory(true);
    setHistoryError(null);
    try {
      const next = await fetchExecutorHistory(['COMPLETED'], page, PAGE_SIZE);
      setHistory((prev) => (append && prev != null ? [...prev, ...next] : next));
      setHistoryPage(page);
      setHasMoreHistory(next.length === PAGE_SIZE);
    } catch {
      setHistoryError(MY_TASKS_COPY.historyError);
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  // Первая загрузка + обновление истории при live-изменениях
  // (завершение задачи убирает её из state.notifications).
  useEffect(() => {
    setHistory(null);
    setHasMoreHistory(true);
    void loadHistory(0, false);
  }, [loadHistory, state.notifications]);

  // Infinite scroll для завершённых задач.
  useEffect(() => {
    const sentinel = historySentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry.isIntersecting && hasMoreHistory && !isLoadingHistory) {
          void loadHistory(historyPage + 1, true);
        }
      },
      { rootMargin: '100px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMoreHistory, isLoadingHistory, historyPage, loadHistory]);

  const handleAction = async (notificationId: number, action: NotificationAction) => {
    setPendingAction({ id: notificationId, action });
    setActionError(null);
    try {
      await updateNotification(notificationId, action);
      void loadHistory(0, false);
    } catch (error) {
      setActionError(getServerErrorMessage(error, MY_TASKS_COPY.actionError));
    } finally {
      setPendingAction(null);
    }
  };

  const showHistorySection = history != null || historyError || isLoadingHistory;

  return (
    <section data-scroll style={PAGE_FADE_SECTION_STYLE}>
      <main className="px-4 pb-6 pt-4 sm:px-6">
        <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3">
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

          {tasks.length === 0 ? (
            <p className="py-16 text-center text-sm font-medium text-[#74777F]">
              {MY_TASKS_COPY.empty}
            </p>
          ) : (
            tasks.map((task) => (
              <NotificationCard
                key={task.notificationId ?? task.unitId}
                notification={task}
                currentUserId={userId}
                onAction={handleAction}
                pendingAction={
                  pendingAction && pendingAction.id === task.notificationId
                    ? pendingAction.action
                    : null
                }
              />
            ))
          )}

          {showHistorySection && (
            <h2 className="mt-4 text-[0.72rem] font-semibold uppercase tracking-wide text-[#74777F]">
              {MY_TASKS_COPY.history}
            </h2>
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
                      {MY_TASKS_COPY.historyEmpty}
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
