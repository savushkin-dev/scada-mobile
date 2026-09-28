import { CARD_TITLE_BETWEEN_STYLE, DOMAIN_DEFAULTS, UI_COPY } from '../../config';
import { DEVICE_STATUS_CLASS, getDeviceStatusLevel } from '../../constants/statusUtils';
import { useDetailsContext } from '../../context/DetailsContext';
import { TabContentState } from '../TabContentState';
import { DevicesTabSkeleton } from '../skeleton/DevicesTabSkeleton';
import type { DevicesStatusPayload, DevicesTopology } from '../../types';

/**
 * Вкладка "Устройства".
 *
 * Модель данных двухслойная:
 * - topology (какие устройства есть) приходит из REST;
 * - live-статус (что с ними сейчас) приходит из unit WS.
 *
 * Доменная логика вычисления статуса устройства находится в
 * {@link ../../constants/statusUtils.ts}; в этом файле только отображение.
 */

// ── Конфигурация групп ─────────────────────────────────────────────────────────
// Статичная таблица соответствия: ключ topology.devices → тип устройства + поведение.
// Заголовок группы берётся из справочника типов (topology.typeNames по typeCode),
// fallbackTitle — запасной вариант, если типа нет в ответе.
// Порядок задаёт порядок отрисовки карточек.
interface GroupConfig {
  key: keyof DevicesTopology['devices'];
  typeCode: string;
  fallbackTitle: string;
  icon: string;
  showStats: boolean;
}

const DEVICE_GROUPS: GroupConfig[] = [
  {
    key: 'printers',
    typeCode: 'printer',
    fallbackTitle: UI_COPY.devicesGroupPrinters,
    icon: '/assets/printer.svg',
    showStats: false,
  },
  {
    key: 'aggregationCams',
    typeCode: 'aggregation_cam',
    fallbackTitle: UI_COPY.devicesGroupAggrCams,
    icon: '/assets/camera.svg',
    showStats: true,
  },
  {
    key: 'aggregationBoxCams',
    typeCode: 'aggregation_box_cam',
    fallbackTitle: UI_COPY.devicesGroupAggrBoxCams,
    icon: '/assets/camera.svg',
    showStats: true,
  },
  {
    key: 'checkerCams',
    typeCode: 'checker_cam',
    fallbackTitle: UI_COPY.devicesGroupCheckerCams,
    icon: '/assets/search.svg',
    showStats: true,
  },
];

// ── Отображение бейджа по уровню статуса ──────────────────────────────────────
// Единый источник правды: status level → badge variant + label.
// Аналогично UNIT_STATUS_CLASS в statusUtils, но для display-слоя.

function val(v: string | number | undefined | null): string {
  return v === null || v === undefined ? DOMAIN_DEFAULTS.emptyValue : String(v);
}

// ── Хелперы группового рендера (topology.groups) ───────────────────────────────

/** Отображаемое имя устройства: per-unit override → имя справочника → код. */
function deviceDisplayName(topology: DevicesTopology | null, code: string): string {
  return topology?.deviceMeta[code]?.displayName ?? topology?.deviceNames[code] ?? code;
}

/** Принадлежность к типу определяется по legacy-массивам topology.devices. */
function isPrinter(topology: DevicesTopology | null, code: string): boolean {
  return topology?.devices.printers.includes(code) ?? false;
}

/** Иконка группы — по типу первого устройства в ней. */
function groupIcon(topology: DevicesTopology | null, firstCode: string): string {
  if (topology === null) return '/assets/camera.svg';
  if (isPrinter(topology, firstCode)) return '/assets/printer.svg';
  if (topology.devices.checkerCams.includes(firstCode)) return '/assets/search.svg';
  return '/assets/camera.svg';
}

// ── Одна карточка устройства ───────────────────────────────────────────────────
// code — технический код устройства (ключ live-данных WS), label — отображаемое
// имя из справочника (device_catalog.name).
// batch — текущая партия из topology (deviceMeta.currentBatch); при отсутствии
// fallback на live-значение WS. Показывается на каждой карточке всегда.
function DeviceCard({
  code,
  label,
  batch,
  wsData,
  showStats,
}: {
  code: string;
  label: string;
  batch: string | null | undefined;
  wsData: DevicesStatusPayload | null;
  showStats: boolean;
}) {
  const statusLevel = getDeviceStatusLevel(wsData, code);
  const statusClass = DEVICE_STATUS_CLASS[statusLevel];
  const info = wsData?.[code];
  const isDisconnected = statusLevel === 'disconnected';

  return (
    <div
      className={`card p-4 card-static mb-3 ${statusClass} ${isDisconnected ? 'opacity-60' : ''}`}
    >
      <div className="card-title" style={CARD_TITLE_BETWEEN_STYLE}>
        <span>{label}</span>
        {isDisconnected && (
          <span className="badge badge-secondary">{UI_COPY.deviceDisconnectedLabel}</span>
        )}
      </div>
      <div className="kv-row mt-2">
        <div className="kv-key">{UI_COPY.currentBatchLabel}</div>
        <div className="kv-val" title={val(batch ?? info?.batch)}>
          {val(batch ?? info?.batch)}
        </div>
      </div>
      {showStats && !isDisconnected && (
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
      {isDisconnected && (
        <div className="kv-row mt-2 text-secondary">{UI_COPY.deviceDisconnectedHint}</div>
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

  // Основной путь: раскладка по группам из topology.groups (per-unit конфиг
  // на backend: «<Имя машины>», «Поток», «Поток 2», «Агрегация»…).
  // Legacy-рендер по типам устройств — только как fallback, если groups пуст.
  const groups = topology?.groups ?? [];
  const hasGroups = groups.length > 0;

  const allEmpty = hasGroups
    ? groups.every((g) => g.codes.length === 0)
    : topology !== null && DEVICE_GROUPS.every((g) => topology.devices[g.key].length === 0);

  return (
    <TabContentState
      isLoading={isLoading}
      error={topologyFailError}
      skeleton={<DevicesTabSkeleton />}
    >
      <>
        {allEmpty ? (
          <div className="card p-4 card-static text-center text-secondary">
            {UI_COPY.devicesNoneConfigured}
          </div>
        ) : hasGroups ? (
          [...groups]
            .sort((a, b) => a.order - b.order)
            .map((group) => {
              if (group.codes.length === 0) return null;
              const icon = groupIcon(topology, group.codes[0]);
              return (
                <section key={`${group.order}:${group.label}`} className="mb-2">
                  <h2 className="section-header mb-2 flex items-center gap-2">
                    <img src={icon} alt="" aria-hidden="true" className="h-5 w-5" />
                    {group.label}
                  </h2>
                  {group.codes.map((code) => (
                    <DeviceCard
                      key={code}
                      code={code}
                      label={deviceDisplayName(topology, code)}
                      batch={topology?.deviceMeta[code]?.currentBatch}
                      wsData={data}
                      showStats={topology?.deviceMeta[code]?.showCounters === true}
                    />
                  ))}
                </section>
              );
            })
        ) : (
          DEVICE_GROUPS.map(({ key, typeCode, fallbackTitle, icon, showStats }) => {
            const codes = topology?.devices[key] ?? [];
            if (codes.length === 0) return null;
            const title = topology?.typeNames[typeCode] ?? fallbackTitle;
            return (
              <section key={key} className="mb-2">
                <h2 className="section-header mb-2 flex items-center gap-2">
                  <img src={icon} alt="" aria-hidden="true" className="h-5 w-5" />
                  {title}
                </h2>
                {codes.map((code) => (
                  <DeviceCard
                    key={code}
                    code={code}
                    label={topology?.deviceNames[code] ?? code}
                    batch={topology?.deviceMeta[code]?.currentBatch}
                    wsData={data}
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
