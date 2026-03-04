import type { AlertData, Workshop } from '../types';

interface Props {
  workshop: Workshop;
  alerts: Map<string, AlertData>;
  onClick: () => void;
}

function getWorkshopStatus(
  workshopId: string,
  alerts: Map<string, AlertData>
): 'critical' | 'warning' | 'none' {
  let hasCritical = false;
  let hasWarning = false;
  alerts.forEach((alert) => {
    if (alert.workshopId === workshopId) {
      if (alert.severity === 'Critical') hasCritical = true;
      else if (alert.severity === 'Warning') hasWarning = true;
    }
  });
  if (hasCritical) return 'critical';
  if (hasWarning) return 'warning';
  return 'none';
}

export function WorkshopCard({ workshop, alerts, onClick }: Props) {
  const status = getWorkshopStatus(workshop.id, alerts);
  const statusClass =
    status === 'critical'
      ? 'status-critical'
      : status === 'warning'
        ? 'status-warning'
        : 'status-normal';

  const hasProblems = workshop.problemUnits > 0;

  return (
    <div className={`card p-5 ${statusClass}`} onClick={onClick}>
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
