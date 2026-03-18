import {
  getUnitErrorGroups,
  getUnitStatusLevel,
  UNIT_STATUS_CLASS,
} from '../constants/statusUtils';
import { UnitErrorBoard } from './UnitErrorBoard';
import type { AlertData, Unit } from '../types';

/**
 * Карточка аппарата на экране цеха.
 *
 * Единый источник правды по статусу и группировке ошибок:
 * {@link ../constants/statusUtils.ts}. Компонент только отображает результат,
 * не повторяет доменные правила.
 */

interface Props {
  unit: Unit;
  alerts: Map<string, AlertData>;
  onClick: () => void;
}

export function UnitCard({ unit, alerts, onClick }: Props) {
  const statusLevel = getUnitStatusLevel(unit, alerts);
  const isPending = statusLevel === 'pending';
  const isOffline = statusLevel === 'offline';
  const isCritical = statusLevel === 'critical';

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
      <h3 className="font-bold text-lg mb-1">{unit.unit}</h3>
      {isCritical && errorGroups.length > 0 ? (
        <UnitErrorBoard groups={errorGroups} />
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
