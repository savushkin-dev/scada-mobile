import {
  CARD_TITLE_BETWEEN_STYLE,
  DEVICE_SECTION_CONFIG,
  DOMAIN_DEFAULTS,
  DOMAIN_FLAGS,
  UI_COPY,
} from '../../config';
import type { DevicesStatusPayload } from '../../types';

interface Props {
  data: DevicesStatusPayload | null;
}

type BadgeVariant = 'success' | 'danger' | 'neutral';

function getBadge(state?: number, error?: number): { variant: BadgeVariant; label: string } {
  if (error === DOMAIN_FLAGS.active) return { variant: 'danger', label: UI_COPY.statusError };
  if (state === DOMAIN_FLAGS.active) return { variant: 'success', label: UI_COPY.statusWorking };
  return { variant: 'neutral', label: UI_COPY.statusStopped };
}

function StatusBadge({ state, error }: { state?: number; error?: number }) {
  const { variant, label } = getBadge(state, error);
  return <span className={`badge ${variant}`}>{label}</span>;
}

function val(v: string | number | undefined | null): string {
  return v === null || v === undefined ? DOMAIN_DEFAULTS.emptyValue : String(v);
}

export function DevicesTab({ data }: Props) {
  const deviceData = data ?? {};

  return (
    <>
      {DEVICE_SECTION_CONFIG.map(({ key, title, showBatch, stats }) => {
        const device = deviceData[key];
        return (
          <div key={key} className="card p-5 card-static mb-4">
            <div className="card-title" style={CARD_TITLE_BETWEEN_STYLE}>
              <span>{title}</span>
              <StatusBadge state={device?.state} error={device?.error} />
            </div>
            {showBatch && (
              <div className="kv-row">
                <div className="kv-key">{UI_COPY.currentBatchLabel}</div>
                <div className="kv-val">{val(device?.batch)}</div>
              </div>
            )}
            {!!stats?.length && (
              <div className="device-stats">
                {stats.map(({ key: statKey, label, danger }) => (
                  <div key={statKey} className={`stat-box${danger ? ' danger' : ''}`}>
                    <div className="stat-val">{device?.[statKey] ?? DOMAIN_DEFAULTS.zeroCount}</div>
                    <div className="stat-label">{label}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </>
  );
}
