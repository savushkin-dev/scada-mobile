import type { DevicesStatusPayload } from '../../types';

interface Props {
  data: DevicesStatusPayload | null;
}

type BadgeVariant = 'success' | 'danger' | 'neutral';

function getBadge(state?: number, error?: number): { variant: BadgeVariant; label: string } {
  if (error === 1) return { variant: 'danger', label: 'Ошибка' };
  if (state === 1) return { variant: 'success', label: 'В работе' };
  return { variant: 'neutral', label: 'Остановлен' };
}

function StatusBadge({ state, error }: { state?: number; error?: number }) {
  const { variant, label } = getBadge(state, error);
  return <span className={`badge ${variant}`}>{label}</span>;
}

function val(v: string | number | undefined | null): string {
  return v === null || v === undefined ? '-' : String(v);
}

export function DevicesTab({ data }: Props) {
  const { printer, cam41, cam42 } = data ?? {};

  return (
    <>
      <div className="card p-5 card-static mb-4">
        <div className="card-title" style={{ justifyContent: 'space-between' }}>
          <span>🖨️ Принтер 1</span>
          <StatusBadge state={printer?.state} error={printer?.error} />
        </div>
        <div className="kv-row">
          <div className="kv-key">Текущая партия</div>
          <div className="kv-val">{val(printer?.batch)}</div>
        </div>
      </div>

      <div className="card p-5 card-static mb-4">
        <div className="card-title" style={{ justifyContent: 'space-between' }}>
          <span>📷 Камера 41 (Поток)</span>
          <StatusBadge state={cam41?.state} error={cam41?.error} />
        </div>
        <div className="kv-row">
          <div className="kv-key">Текущая партия</div>
          <div className="kv-val">{val(cam41?.batch)}</div>
        </div>
        <div className="device-stats">
          <div className="stat-box">
            <div className="stat-val">{cam41?.read ?? 0}</div>
            <div className="stat-label">Считано</div>
          </div>
          <div className="stat-box danger">
            <div className="stat-val">{cam41?.unread ?? 0}</div>
            <div className="stat-label">Несчитано</div>
          </div>
        </div>
      </div>

      <div className="card p-5 card-static mb-4">
        <div className="card-title" style={{ justifyContent: 'space-between' }}>
          <span>📷 Камера 42 (Поток)</span>
          <StatusBadge state={cam42?.state} error={cam42?.error} />
        </div>
        <div className="device-stats">
          <div className="stat-box">
            <div className="stat-val">{cam42?.read ?? 0}</div>
            <div className="stat-label">Считано</div>
          </div>
          <div className="stat-box danger">
            <div className="stat-val">{cam42?.unread ?? 0}</div>
            <div className="stat-label">Несчитано</div>
          </div>
        </div>
      </div>
    </>
  );
}
