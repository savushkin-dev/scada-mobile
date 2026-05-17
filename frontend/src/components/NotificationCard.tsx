import { UI_PALETTE } from '../config';
import type { NotificationData } from '../types';

interface Props {
  notification: NotificationData & { unitId: string };
}

const NOTIFICATION_COPY = Object.freeze({
  eventLabel: 'Последняя партия',
  unitLabel: 'Автомат',
  operatorLabel: 'Оператор',
});

/**
 * Карточка производственного уведомления.
 *
 * Единый жёлтый (warning) стиль для всех уведомлений от работников.
 * Отображает: тип события, название автомата, ФИО оператора.
 */
export function NotificationCard({ notification }: Props) {
  return (
    <div
      className="card p-4"
      style={{
        backgroundColor: UI_PALETTE.warningBg,
        borderColor: UI_PALETTE.warning,
      }}
    >
      <h3 className="text-base font-bold text-[#1A1C1E] mb-2">{NOTIFICATION_COPY.eventLabel}</h3>

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
    </div>
  );
}
