import { UI_PALETTE } from '../config';
import type { NotificationData } from '../types';
import type { NotificationAction } from '../api/notifications';

interface Props {
  notification: NotificationData & { unitId: string };
  onAction?: (notificationId: number, action: NotificationAction) => void;
  pendingAction?: NotificationAction | null;
}

const NOTIFICATION_COPY = Object.freeze({
  eventLabel: 'Последняя партия',
  unitLabel: 'Автомат',
  operatorLabel: 'Оператор',
  pending: 'Ожидает',
  inProgress: 'В работе',
  completed: 'Выполнено',
  cancelled: 'Отменено',
  acceptedBy: 'Принял',
  acceptedAt: 'Время принятия',
  accept: 'Принять',
  complete: 'Готово',
  cancel: 'Отменить',
});

/**
 * Карточка производственного уведомления.
 *
 * Единый жёлтый (warning) стиль для всех уведомлений от работников.
 * Отображает: тип события, название автомата, ФИО оператора.
 */
export function NotificationCard({ notification, onAction, pendingAction }: Props) {
  const status = notification.status ?? 'PENDING';
  const statusLabel = {
    PENDING: NOTIFICATION_COPY.pending,
    IN_PROGRESS: NOTIFICATION_COPY.inProgress,
    COMPLETED: NOTIFICATION_COPY.completed,
    CANCELLED: NOTIFICATION_COPY.cancelled,
  }[status];
  const canAct = notification.notificationId != null && pendingAction == null;

  return (
    <div
      className="card p-4"
      style={{
        backgroundColor: UI_PALETTE.warningBg,
        borderColor: UI_PALETTE.warning,
      }}
    >
      <h3 className="text-base font-bold text-[#1A1C1E] mb-2">{NOTIFICATION_COPY.eventLabel}</h3>
      <span className="mb-3 inline-flex rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-[#1A1C1E]">
        {statusLabel}
      </span>

      <div className="space-y-1.5">
        <div>
          <span className="block text-[0.72rem] font-semibold text-gray-500 uppercase tracking-wide leading-none">
            {NOTIFICATION_COPY.unitLabel}
          </span>
          <span className="block text-[0.9rem] font-medium text-gray-900 leading-snug">
            {notification.unitName}
          </span>
        </div>

        {(notification.creatorName || notification.creatorId) && (
          <div>
            <span className="block text-[0.72rem] font-semibold text-gray-500 uppercase tracking-wide leading-none">
              {NOTIFICATION_COPY.operatorLabel}
            </span>
            <span className="block text-[0.9rem] font-medium text-gray-900 leading-snug">
              {notification.creatorName || notification.creatorId}
            </span>
          </div>
        )}
      </div>

      {status === 'IN_PROGRESS' && notification.acceptedBy && (
        <div className="mt-3 border-t border-black/10 pt-3 text-sm text-gray-700">
          <div>
            {NOTIFICATION_COPY.acceptedBy}: {notification.acceptedBy}
          </div>
          {notification.acceptedAt && (
            <div>
              {NOTIFICATION_COPY.acceptedAt}: {new Date(notification.acceptedAt).toLocaleString()}
            </div>
          )}
        </div>
      )}

      {onAction && notification.notificationId != null && (
        <div className="mt-4 flex flex-wrap gap-2">
          {status === 'PENDING' && (
            <button
              type="button"
              className="min-h-11 rounded-lg bg-[#1D4ED8] px-4 py-2 text-sm font-bold text-white disabled:opacity-60"
              disabled={!canAct}
              onClick={() => onAction(notification.notificationId!, 'accept')}
            >
              {pendingAction === 'accept' ? '...' : NOTIFICATION_COPY.accept}
            </button>
          )}
          {status === 'IN_PROGRESS' && (
            <button
              type="button"
              className="min-h-11 rounded-lg bg-[#15803D] px-4 py-2 text-sm font-bold text-white disabled:opacity-60"
              disabled={!canAct}
              onClick={() => onAction(notification.notificationId!, 'complete')}
            >
              {pendingAction === 'complete' ? '...' : NOTIFICATION_COPY.complete}
            </button>
          )}
          {status === 'PENDING' && (
            <button
              type="button"
              className="min-h-11 rounded-lg border border-[#B91C1C] px-4 py-2 text-sm font-bold text-[#B91C1C] disabled:opacity-60"
              disabled={!canAct}
              onClick={() => onAction(notification.notificationId!, 'cancel')}
            >
              {pendingAction === 'cancel' ? '...' : NOTIFICATION_COPY.cancel}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
