import { CARD_TITLE_BETWEEN_STYLE, DOMAIN_DEFAULTS, UI_COPY } from '../../config';
import {
  DEVICE_STATUS_CLASS,
  DeviceStatusLevel,
  getDeviceStatusLevel,
} from '../../constants/statusUtils';
import { useDetailsContext } from '../../context/DetailsContext';
import { TabContentState } from '../TabContentState';
import { DevicesTabSkeleton } from '../skeleton/DevicesTabSkeleton';
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
    devicesLoading,
    devicesData: data,
    pageError,
  } = useDetailsContext();

  // Topology используется как основной источник контента для этой вкладки.
  // WS-данные (devicesData) показывают статус устройств поверх topology.
  const isLoading = devicesLoading && topology === null && pageError === null;
  // topology===null и есть ошибка (REST или WS) → показать ошибку.
  const topologyFailError = topology === null && pageError !== null ? pageError : null;

  const allEmpty =
    topology !== null && DEVICE_GROUPS.every((g) => topology.devices[g.key].length === 0);

  return (
    <TabContentState
      isLoading={isLoading}
      error={topologyFailError}
      skeleton={<DevicesTabSkeleton />}
    >
      <>
        {allEmpty ? (
          <div className="card p-5 card-static text-center text-secondary">
            {UI_COPY.devicesNoneConfigured}
          </div>
        ) : (
          DEVICE_GROUPS.map(({ key, title, showBatch, showStats }) => {
            const names = topology?.devices[key] ?? [];
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
          })
        )}
      </>
    </TabContentState>
  );
}
