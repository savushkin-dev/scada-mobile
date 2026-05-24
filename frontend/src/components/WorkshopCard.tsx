import { UI_COPY, UI_PALETTE } from '../config';
import {
  getWorkshopErrorGroups,
  getWorkshopNotificationGroups,
  getWorkshopStatusLevel,
  WORKSHOP_STATUS_CLASS,
} from '../constants/statusUtils';
import { UnitErrorBoard } from './UnitErrorBoard';
import type { AlertData, NotificationData, Workshop } from '../types';

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
  notifications?: Map<string, NotificationData>;
  unitTopologyByWorkshop?: Record<string, { id: string; unit: string }[]>;
  onClick: () => void;
}

export function WorkshopCard({
  workshop,
  alerts,
  notifications,
  unitTopologyByWorkshop,
  onClick,
}: Props) {
  const status = getWorkshopStatusLevel(workshop.id, alerts, notifications, unitTopologyByWorkshop);
  const statusClass = WORKSHOP_STATUS_CLASS[status];
  const isCritical = status === 'critical';
  const isWarning = status === 'warning';

  const errorGroups = isCritical ? getWorkshopErrorGroups(workshop.id, alerts) : [];
  const notificationGroups =
    isWarning && notifications && unitTopologyByWorkshop
      ? getWorkshopNotificationGroups(workshop.id, notifications, unitTopologyByWorkshop)
      : [];

  return (
    <div className={`card p-5 md:h-full ${statusClass}`} onClick={onClick}>
      <h2 className="text-xl font-bold mb-1">{workshop.name}</h2>
      <p className="text-xs text-gray-500 font-medium">
        {UI_COPY.workshopTotalUnitsLabel}:{' '}
        <span className="text-gray-900">{workshop.totalUnits}</span>
      </p>
      {isCritical && errorGroups.length > 0 && <UnitErrorBoard groups={errorGroups} />}
      {isWarning && notificationGroups.length > 0 && (
        <div className="mt-2 pt-2 border-t border-[#FCD34D]">
          {notificationGroups.map((group, gi) => (
            <div key={group.unitId}>
              {gi > 0 && <hr className="border-[#FCD34D] my-1.5" />}
              <div className="mb-1 flex items-center justify-between gap-2">
                <p
                  className="text-[0.8rem] font-bold leading-tight"
                  style={{ color: UI_PALETTE.warningText }}
                >
                  {group.unitName}
                </p>
                <span className="inline-flex items-center text-xs" aria-hidden="true">
                  <img
                    src="/assets/bell.svg"
                    alt=""
                    className="h-3.5 w-3.5"
                    style={{ filter: 'none' }}
                  />
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
