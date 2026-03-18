import { UI_COPY } from '../config';
import {
  getWorkshopErrorGroups,
  getWorkshopStatusLevel,
  WORKSHOP_STATUS_CLASS,
} from '../constants/statusUtils';
import { UnitErrorBoard } from './UnitErrorBoard';
import type { AlertData, Workshop } from '../types';

/**
 * Карточка цеха на dashboard.
 *
 * Показывает агрегированный статус цеха и, при критике, табло ошибок
 * проблемных аппаратов. Бизнес-вычисления статуса берутся из
 * {@link ../constants/statusUtils.ts}.
 */

interface Props {
  workshop: Workshop;
  alerts: Map<string, AlertData>;
  onClick: () => void;
}

export function WorkshopCard({ workshop, alerts, onClick }: Props) {
  const status = getWorkshopStatusLevel(workshop.id, alerts);
  const statusClass = WORKSHOP_STATUS_CLASS[status];
  const isCritical = status === 'critical';

  const errorGroups = isCritical ? getWorkshopErrorGroups(workshop.id, alerts) : [];

  return (
    <div className={`card p-5 md:h-full ${statusClass}`} onClick={onClick}>
      <h2 className="text-xl font-bold mb-1">{workshop.name}</h2>
      <p className="text-xs text-gray-500 font-medium">
        {UI_COPY.workshopTotalUnitsLabel}:{' '}
        <span className="text-gray-900">{workshop.totalUnits}</span>
      </p>
      {isCritical && errorGroups.length > 0 && <UnitErrorBoard groups={errorGroups} />}
    </div>
  );
}
