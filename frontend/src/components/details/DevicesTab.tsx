import { CARD_TITLE_BETWEEN_STYLE, DOMAIN_DEFAULTS, UI_COPY } from '../../config';
import {
  DEVICE_STATUS_CLASS,
  DeviceStatusLevel,
  getDeviceStatusLevel,
} from '../../constants/statusUtils';
import { useDetailsContext } from '../../context/DetailsContext';
import { SkeletonBlock } from '../skeleton/SkeletonBlock';
import type { DevicesStatusPayload, DevicesTopology } from '../../types';

// ── Конфигурация групп ─────────────────────────────────────────────────────────
// Статичная таблица соответствия: ключ topology.devices → заголовок + поведение.
// Порядок задаёт порядок отрисовки карточек.
interface GroupConfig {
  key: keyof DevicesTopology['devices'];
  title: string;
  showBatch: boolean;
  showStats: boolean;
}

const DEVICE_GROUPS: GroupConfig[] = [
  { key: 'printers', title: UI_COPY.devicesGroupPrinters, showBatch: true, showStats: false },
  {
    key: 'aggregationCams',
    title: UI_COPY.devicesGroupAggrCams,
    showBatch: false,
    showStats: true,
  },
  {
    key: 'aggregationBoxCams',
    title: UI_COPY.devicesGroupAggrBoxCams,
    showBatch: false,
    showStats: true,
  },
  {
    key: 'checkerCams',
    title: UI_COPY.devicesGroupCheckerCams,
    showBatch: false,
    showStats: true,
  },
];

// ── Отображение бейджа по уровню статуса ──────────────────────────────────────
// Единый источник правды: status level → badge variant + label.
// Аналогично UNIT_STATUS_CLASS в statusUtils, но для display-слоя.

type BadgeVariant = 'success' | 'danger' | 'neutral';

const DEVICE_BADGE_VARIANT: Record<DeviceStatusLevel, BadgeVariant> = {
  pending: 'neutral',
  error: 'danger',
  working: 'success',
  stopped: 'neutral',
};

const DEVICE_BADGE_LABEL: Record<DeviceStatusLevel, string> = {
  pending: UI_COPY.devicesPending,
  error: UI_COPY.statusError,
  working: UI_COPY.statusWorking,
  stopped: UI_COPY.statusStopped,
};

function val(v: string | number | undefined | null): string {
  return v === null || v === undefined ? DOMAIN_DEFAULTS.emptyValue : String(v);
}

// ── Скелетон (первая загрузка, topology ещё null) ─────────────────────────────
function DevicesSkeleton() {
  return (
    <>
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} aria-hidden="true" className="card p-5 card-static mb-4">
          <div style={CARD_TITLE_BETWEEN_STYLE}>
            <SkeletonBlock height="18px" width="55%" borderRadius="6px" />
            <SkeletonBlock height="22px" width="72px" borderRadius="12px" />
          </div>
          <div style={{ marginTop: '14px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <SkeletonBlock height="14px" width="80%" borderRadius="4px" />
            <SkeletonBlock height="14px" width="65%" borderRadius="4px" />
          </div>
        </div>
      ))}
    </>
  );
}

// ── Одна карточка устройства ───────────────────────────────────────────────────
function DeviceCard({
  name,
  wsData,
  showBatch,
  showStats,
}: {
  name: string;
  wsData: DevicesStatusPayload | null;
  showBatch: boolean;
  showStats: boolean;
}) {
  const statusLevel = getDeviceStatusLevel(wsData, name);
  const statusClass = DEVICE_STATUS_CLASS[statusLevel];
  const info = wsData?.[name];

  return (
    <div className={`card p-4 card-static mb-3 ${statusClass}`}>
      <div className="card-title" style={CARD_TITLE_BETWEEN_STYLE}>
        <span>{name}</span>
        <span className={`badge ${DEVICE_BADGE_VARIANT[statusLevel]}`}>
          {DEVICE_BADGE_LABEL[statusLevel]}
        </span>
      </div>
      {showBatch && (
        <div className="kv-row mt-2">
          <div className="kv-key">{UI_COPY.currentBatchLabel}</div>
          <div className="kv-val">{val(info?.batch)}</div>
        </div>
      )}
      {showStats && (
        <div className="device-stats mt-2">
          <div className="stat-box">
            <div className="stat-val">{info?.read ?? DOMAIN_DEFAULTS.zeroCount}</div>
            <div className="stat-label">{UI_COPY.devicesStatRead}</div>
          </div>
          <div className="stat-box danger">
            <div className="stat-val">{info?.unread ?? DOMAIN_DEFAULTS.zeroCount}</div>
            <div className="stat-label">{UI_COPY.devicesStatUnread}</div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Основной компонент ─────────────────────────────────────────────────────────
export function DevicesTab() {
  const {
    devicesTopology: topology,
    devicesLoading: isLoading,
    devicesData: data,
  } = useDetailsContext();

  if (isLoading && topology === null) {
    return <DevicesSkeleton />;
  }

  if (topology === null) {
    return (
      <div className="card p-5 card-static text-center text-secondary">
        {UI_COPY.devicesNoTopology}
      </div>
    );
  }

  const allEmpty = DEVICE_GROUPS.every((g) => topology.devices[g.key].length === 0);
  if (allEmpty) {
    return (
      <div className="card p-5 card-static text-center text-secondary">
        {UI_COPY.devicesNoneConfigured}
      </div>
    );
  }

  return (
    <>
      {DEVICE_GROUPS.map(({ key, title, showBatch, showStats }) => {
        const names = topology.devices[key];
        if (names.length === 0) return null;
        return (
          <section key={key} className="mb-2">
            <h2 className="section-header mb-2">{title}</h2>
            {names.map((name) => (
              <DeviceCard
                key={name}
                name={name}
                wsData={data}
                showBatch={showBatch}
                showStats={showStats}
              />
            ))}
          </section>
        );
      })}
    </>
  );
}
