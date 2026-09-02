import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { UI_PALETTE } from '../config';
import type { NotificationData } from '../types';
import type { NotificationAction } from '../api/notifications';
import { ConfirmationOverlay } from './ConfirmationOverlay';

interface Props {
  notification: NotificationData & { unitId: string };
  /** Id текущего пользователя — определяет, какие действия доступны на карточке. */
  currentUserId?: string | null;
  onAction?: (notificationId: number, action: NotificationAction) => void;
  pendingAction?: NotificationAction | null;
}

type NotificationStatus = NonNullable<NotificationData['status']>;

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
  completedAt: 'Завершено',
  cancelledAt: 'Отменено в',
  accept: 'Принять',
  complete: 'Готово',
  finish: 'Завершить',
  cancel: 'Отменить',
});

/** Порог «зависшего» уведомления: PENDING старше 10 минут визуально выделяется. */
const STALE_THRESHOLD_MS = 10 * 60 * 1000;
/** Период пересчёта «свежести», чтобы подсветка появлялась без внешнего ре-рендера. */
const STALE_TICK_MS = 30 * 1000;

/** Цветовая схема карточки по статусу (согласована с токенами index.css). */
const STATUS_STYLE: Record<NotificationStatus, { backgroundColor: string; borderColor: string }> = {
  PENDING: { backgroundColor: UI_PALETTE.warningBg, borderColor: UI_PALETTE.warning },
  IN_PROGRESS: { backgroundColor: '#F0F7FF', borderColor: '#3B82F6' },
  COMPLETED: { backgroundColor: '#F4FBF6', borderColor: UI_PALETTE.success },
  CANCELLED: { backgroundColor: '#F8F9FA', borderColor: '#BDC1C6' },
};

const STATUS_BADGE_STYLE: Record<NotificationStatus, { backgroundColor: string; color: string }> = {
  PENDING: { backgroundColor: '#FEF3C7', color: UI_PALETTE.warningTextDark },
  IN_PROGRESS: { backgroundColor: '#DBEAFE', color: '#1D4ED8' },
  COMPLETED: { backgroundColor: '#DFF3E4', color: '#1E7E34' },
  CANCELLED: { backgroundColor: '#EDF0F4', color: UI_PALETTE.mutedText },
};

/**
 * Парсит timestamp с бэкенда. WS/REST-время приходит в UTC без маркера
 * зоны (наивный ISO, см. NotificationMessageDTO) — такие значения
 * интерпретируем как UTC, иначе new Date() считает их локальными.
 */
function parseUtcDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const hasZone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(value);
  const date = new Date(hasZone ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateTime(value: string | null | undefined): string | null {
  const date = parseUtcDate(value);
  return date ? date.toLocaleString('ru-RU') : null;
}

/**
 * Карточка производственного уведомления.
 *
 * Цвет карточки и бейджа кодирует статус (Ожидает / В работе / Выполнено /
 * Отменено). Кнопки действий зависят от роли текущего пользователя:
 * - «Принять» — получателю при PENDING;
 * - «Отменить» — создателю при PENDING (с подтверждением);
 * - «Готово» — принявшему исполнителю при IN_PROGRESS;
 * - «Завершить» — создателю при IN_PROGRESS.
 */
export function NotificationCard({ notification, currentUserId, onAction, pendingAction }: Props) {
  const [cancelConfirmationOpen, setCancelConfirmationOpen] = useState(false);
  // Таймер для пересчёта isStale без внешнего ре-рендера.
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), STALE_TICK_MS);
    return () => clearInterval(timer);
  }, []);

  const status: NotificationStatus = notification.status ?? 'PENDING';
  const isCreator = currentUserId != null && notification.creatorId === currentUserId;
  const isExecutor = currentUserId != null && notification.acceptedBy === currentUserId;

  const createdAtMs = parseUtcDate(notification.timestamp)?.getTime() ?? NaN;
  const isStale =
    status === 'PENDING' && !Number.isNaN(createdAtMs) && now - createdAtMs > STALE_THRESHOLD_MS;

  const statusStyle = STATUS_STYLE[status];
  const badgeStyle = STATUS_BADGE_STYLE[status];
  const statusLabel = {
    PENDING: NOTIFICATION_COPY.pending,
    IN_PROGRESS: NOTIFICATION_COPY.inProgress,
    COMPLETED: NOTIFICATION_COPY.completed,
    CANCELLED: NOTIFICATION_COPY.cancelled,
  }[status];

  const canAct = notification.notificationId != null && pendingAction == null;
  const acceptedAtLabel = formatDateTime(notification.acceptedAt);
  const completedAtLabel = formatDateTime(notification.completedAt);
  const cancelledAtLabel = formatDateTime(notification.cancelledAt);

  return (
    <div
      className={`card p-4 ${isStale ? 'animate-pulse' : ''}`}
      style={{
        backgroundColor: statusStyle.backgroundColor,
        borderColor: isStale ? UI_PALETTE.critical : statusStyle.borderColor,
      }}
    >
      <div className="mb-2 flex items-start justify-between gap-3">
        <h3 className="text-base font-bold text-[#1A1C1E]">{NOTIFICATION_COPY.eventLabel}</h3>
        <span
          className="inline-flex shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold"
          style={{ backgroundColor: badgeStyle.backgroundColor, color: badgeStyle.color }}
        >
          {statusLabel}
        </span>
      </div>

      <div className="space-y-1.5">
        <div>
          <span className="block text-[0.72rem] font-semibold text-[#74777F] uppercase tracking-wide leading-none">
            {NOTIFICATION_COPY.unitLabel}
          </span>
          <span className="block text-[0.9rem] font-medium text-[#1A1C1E] leading-snug">
            {notification.unitName}
          </span>
        </div>

        {(notification.creatorName || notification.creatorId) && (
          <div>
            <span className="block text-[0.72rem] font-semibold text-[#74777F] uppercase tracking-wide leading-none">
              {NOTIFICATION_COPY.operatorLabel}
            </span>
            <span className="block text-[0.9rem] font-medium text-[#1A1C1E] leading-snug">
              {notification.creatorName || notification.creatorId}
            </span>
          </div>
        )}
      </div>

      {status === 'IN_PROGRESS' && (notification.acceptedByName || notification.acceptedBy) && (
        <div className="mt-3 border-t border-black/10 pt-3 text-sm text-[#1A1C1E]">
          <div>
            {NOTIFICATION_COPY.acceptedBy}: {notification.acceptedByName ?? notification.acceptedBy}
          </div>
          {acceptedAtLabel && (
            <div className="text-[#74777F]">
              {NOTIFICATION_COPY.acceptedAt}: {acceptedAtLabel}
            </div>
          )}
        </div>
      )}

      {status === 'COMPLETED' && (
        <div className="mt-3 border-t border-black/10 pt-3 text-sm">
          {(notification.acceptedByName || notification.acceptedBy) && (
            <div className="text-[#1A1C1E]">
              {NOTIFICATION_COPY.acceptedBy}:{' '}
              {notification.acceptedByName ?? notification.acceptedBy}
            </div>
          )}
          {completedAtLabel && (
            <div className="text-[#74777F]">
              {NOTIFICATION_COPY.completedAt}: {completedAtLabel}
            </div>
          )}
        </div>
      )}
      {status === 'CANCELLED' && cancelledAtLabel && (
        <div className="mt-3 border-t border-black/10 pt-3 text-sm text-[#74777F]">
          {NOTIFICATION_COPY.cancelledAt}: {cancelledAtLabel}
        </div>
      )}

      {onAction && notification.notificationId != null && (
        <div className="mt-4 flex flex-wrap gap-2">
          {status === 'PENDING' && !isCreator && (
            <button
              type="button"
              className="min-h-11 flex-1 rounded-2xl bg-[#3B82F6] px-4 py-2 text-sm font-bold text-white transition active:scale-[0.98] disabled:opacity-60"
              disabled={!canAct}
              onClick={() => onAction(notification.notificationId!, 'accept')}
            >
              {pendingAction === 'accept' ? '...' : NOTIFICATION_COPY.accept}
            </button>
          )}
          {status === 'IN_PROGRESS' && isExecutor && (
            <button
              type="button"
              className="min-h-11 flex-1 rounded-2xl bg-[#34A853] px-4 py-2 text-sm font-bold text-white transition active:scale-[0.98] disabled:opacity-60"
              disabled={!canAct}
              onClick={() => onAction(notification.notificationId!, 'complete')}
            >
              {pendingAction === 'complete' ? '...' : NOTIFICATION_COPY.complete}
            </button>
          )}
          {status === 'IN_PROGRESS' && isCreator && (
            <button
              type="button"
              className="min-h-11 flex-1 rounded-2xl bg-[#34A853] px-4 py-2 text-sm font-bold text-white transition active:scale-[0.98] disabled:opacity-60"
              disabled={!canAct}
              onClick={() => onAction(notification.notificationId!, 'complete')}
            >
              {pendingAction === 'complete' ? '...' : NOTIFICATION_COPY.finish}
            </button>
          )}
          {status === 'PENDING' && isCreator && (
            <button
              type="button"
              className="min-h-11 rounded-2xl border-2 border-[#EA4335] px-4 py-2 text-sm font-bold text-[#EA4335] transition active:scale-[0.98] disabled:opacity-60"
              disabled={!canAct}
              onClick={() => setCancelConfirmationOpen(true)}
            >
              {pendingAction === 'cancel' ? '...' : NOTIFICATION_COPY.cancel}
            </button>
          )}
        </div>
      )}
      {createPortal(
        <ConfirmationOverlay
          open={cancelConfirmationOpen}
          title="Отменить уведомление?"
          subtitle="Это действие нельзя отменить"
          confirmLabel="Отменить"
          cancelLabel="Назад"
          confirmColor="red"
          onCancel={() => setCancelConfirmationOpen(false)}
          onConfirm={() => {
            setCancelConfirmationOpen(false);
            onAction?.(notification.notificationId!, 'cancel');
          }}
        />,
        document.body
      )}
    </div>
  );
}
