import {
  getUnitErrorGroups,
  getUnitStatusLevel,
  UNIT_STATUS_CLASS,
} from '../constants/statusUtils';
import { UnitErrorBoard } from './UnitErrorBoard';
import type { AlertData, NotificationData, Unit } from '../types';

/**
 * Карточка аппарата на экране цеха.
 *
 * Единый источник правды по статусу и группировке ошибок:
 * {@link ../constants/statusUtils.ts}. Компонент только отображает результат,
 * не повторяет доменные правила.
 *
 * Слой уведомлений: если для аппарата есть активное notification —
 * на карточке показывается индикатор (оранжевый / жёлтый).
 */

interface Props {
  unit: Unit;
  alerts: Map<string, AlertData>;
  /** Активные производственные уведомления (из AppContext). */
  notifications?: Map<string, NotificationData>;
  onClick: () => void;
}

export function UnitCard({ unit, alerts, notifications, onClick }: Props) {
  const statusLevel = getUnitStatusLevel(unit, alerts);
  const isPending = statusLevel === 'pending';
  const isOffline = statusLevel === 'offline';
  const isCritical = statusLevel === 'critical';

  // Notification — отдельный визуальный слой (не заменяет статус).
  const notification = notifications?.get(String(unit.id));

  const statusClass = UNIT_STATUS_CLASS[statusLevel];
  // offline: карточка некликабельна; card-static отключает cursor:pointer и :active-scale.
  const interactiveProps = isOffline
    ? { 'aria-disabled': true as const }
    : { onClick, role: 'button' as const };

  const errorGroups = isCritical ? getUnitErrorGroups(unit.id, alerts) : [];

  return (
    <div
      className={`card p-4 md:h-full ${statusClass}${isOffline ? ' card-static' : ''}`}
      {...interactiveProps}
    >
      <div className="flex items-center gap-2 mb-1">
        <h3 className="font-bold text-lg">{unit.unit}</h3>
        {notification && (
          <span
            className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full"
            style={{
              backgroundColor: '#FFF3CD',
              color: '#856404',
              border: '1px solid #FFECB5',
            }}
            title={
              notification.creatorId
                ? `Уведомление от ${notification.creatorId}`
                : 'Активное уведомление'
            }
          >
            🔔 {notification.creatorId && <span>{notification.creatorId}</span>}
          </span>
        )}
      </div>
      {isCritical && errorGroups.length > 0 ? (
        <UnitErrorBoard groups={errorGroups} />
      ) : notification ? (
        <p className="text-sm mb-3 text-amber-600 italic">
          ⚠ Производственное уведомление
          {notification.creatorId ? ` от ${notification.creatorId}` : ''}
        </p>
      ) : (
        <p
          className={`text-sm mb-3 italic ${isPending || isOffline ? 'text-gray-400' : 'text-gray-500'}`}
        >
          {unit.event}
        </p>
      )}
    </div>
  );
}
