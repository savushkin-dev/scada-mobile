import type { AlertData, Unit } from '../types';

interface Props {
  unit: Unit;
  alerts: Map<string, AlertData>;
  onClick: () => void;
}

export function UnitCard({ unit, alerts, onClick }: Props) {
  const alert = alerts.get(String(unit.id));
  const isCritical = alert?.severity === 'Critical';
  const isWarning = alert?.severity === 'Warning';
  const hasAlert = isCritical || isWarning;
  const timerNotZero = unit.timer && unit.timer !== '00:00:00';

  const statusClass = isCritical
    ? 'status-critical'
    : isWarning
      ? 'status-warning'
      : 'status-normal';

  return (
    <div className={`card p-4 ${statusClass}`} onClick={onClick}>
      <h3 className="font-bold text-lg mb-1">{unit.unit}</h3>
      <p className="text-sm text-gray-500 mb-3 italic">{unit.event}</p>
      {(hasAlert || timerNotZero) && (
        <div
          className={`flex justify-between items-center p-3 rounded-xl ${
            isCritical ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700'
          }`}
        >
          <span className="text-xs font-semibold">ВРЕМЯ ПРОСТОЯ</span>
          <span className="text-xl font-black tabular-nums">{unit.timer}</span>
        </div>
      )}
    </div>
  );
}
