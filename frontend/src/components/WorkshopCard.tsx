import { getWorkshopStatusLevel, WORKSHOP_STATUS_CLASS } from '../constants/statusUtils';
import type { AlertData, Workshop } from '../types';

interface Props {
  workshop: Workshop;
  alerts: Map<string, AlertData>;
  onClick: () => void;
}

export function WorkshopCard({ workshop, alerts, onClick }: Props) {
  const status = getWorkshopStatusLevel(workshop.id, alerts);
  const statusClass = WORKSHOP_STATUS_CLASS[status];

  const hasProblems = workshop.problemUnits > 0;

  return (
    <div className={`card p-5 md:h-full ${statusClass}`} onClick={onClick}>
      <h2 className="text-xl font-bold mb-1">{workshop.name}</h2>
      <div className="flex justify-between items-end">
        <div className="space-y-1">
          <p className="text-xs text-gray-500 font-medium">
            Аппаратов/Линий: <span className="text-gray-900">{workshop.totalUnits}</span>
          </p>
          <p className="text-xs text-gray-500 font-medium">
            Проблемных:{' '}
            <span className={hasProblems ? 'text-amber-600 font-bold' : 'text-gray-900'}>
              {workshop.problemUnits}
            </span>
          </p>
        </div>
        {hasProblems && (
          <span className="text-[10px] font-bold uppercase tracking-tight px-2 py-1 rounded-md bg-amber-100 text-amber-700">
            Есть проблемы
          </span>
        )}
      </div>
    </div>
  );
}
