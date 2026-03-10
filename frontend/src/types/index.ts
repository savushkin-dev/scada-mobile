// ── Navigation ────────────────────────────────────────────────────────
/** URL search-param «?tab=» для детальной страницы аппарата. */
export type TabId = 'tab-batch' | 'tab-devices' | 'tab-queue' | 'tab-logs';

// ── Domain models ─────────────────────────────────────────────────────
export type AlertSeverity = 'Critical' | 'Warning';

export interface AlertData {
  severity: AlertSeverity;
  errors: unknown[];
  timestamp: string;
  workshopId: string;
}

// ── Topology (статика, загружается один раз, кэшируется) ──────────────

/** Статическая топология цеха — данные из GET /workshops/topology */
export interface WorkshopTopology {
  id: string;
  name: string;
  totalUnits: number;
}

/** Статическая топология аппарата — данные из GET /workshops/{id}/units/topology */
export interface UnitTopology {
  id: string;
  workshopId: string;
  unit: string;
}

/**
 * Статическая топология устройств аппарата —
 * данные из GET /workshops/{id}/units/{unitId}/devices/topology.
 *
 * Тот же ETag, что у остальных topology-эндпоинтов.
 * Устройства сгруппированы по функциональной роли.
 */
export interface DevicesTopology {
  unitId: string;
  workshopId: string;
  unit: string;
  devices: {
    /** Принтеры маркировки (может быть пустым, например, у trepko). */
    printers: string[];
    /** Камеры агрегации на потоке (DataMatrix). */
    aggregationCams: string[];
    /** Камеры агрегации на коробе. */
    aggregationBoxCams: string[];
    /** Камеры проверки (EAN, DataMatrix и др.). */
    checkerCams: string[];
  };
}

// ── Live status (поступает по WebSocket) ─────────────────────────────

/** Live-статус цеха из WS /ws/workshops/status */
export interface WorkshopStatus {
  workshopId: string;
  problemUnits: number;
}

/** Live-статус аппарата из WS /ws/workshops/{id}/units/status */
export interface UnitStatus {
  unitId: string;
  workshopId: string;
  event: string;
  timer: string;
}

/** WS-конверт для статуса цехов */
export interface WorkshopsStatusMessage {
  type: 'WORKSHOPS_STATUS';
  payload: WorkshopStatus[];
}

/** WS-конверт для статуса аппаратов цеха */
export interface UnitsStatusMessage {
  type: 'UNITS_STATUS';
  workshopId: string;
  payload: UnitStatus[];
}

/**
 * WS-сообщение типа ALERT_SNAPSHOT — начальный срез активных алёртов,
 * отправляемый сервером сразу после установки соединения /ws/live.
 * payload-элементы имеют ту же форму, что и AlertWsMessage (active всегда true).
 */
export interface AlertSnapshotMessage {
  type: 'ALERT_SNAPSHOT';
  payload: AlertWsMessage[];
}

/** Действия, отправляемые клиентом на сервер через /ws/live */
export type LiveWsClientAction =
  | { action: 'SUBSCRIBE_WORKSHOP'; workshopId: string }
  | { action: 'UNSUBSCRIBE_WORKSHOP' };

/** Объединённый тип всех входящих сообщений /ws/live */
export type LiveWsIncomingMessage = AlertSnapshotMessage | UnitsStatusMessage | AlertWsMessage;

// ── Merged view types (topology + status, используются компонентами) ──

/** Полные данные цеха для UI — topology + статус объединённые */
export interface Workshop {
  id: string;
  name: string;
  totalUnits: number;
  problemUnits: number;
}

/** Полные данные аппарата для UI — topology + статус объединённые */
export interface Unit {
  id: string;
  workshopId: string;
  unit: string;
  event: string;
  timer: string;
  /**
   * `true` — WS-статус для этого аппарата уже получен.
   * `false` — топология загружена, но первый UNITS_STATUS ещё не пришёл.
   *
   * Используется в {@link getUnitStatusLevel} для возврата `'pending'` вместо
   * `'warning'`, чтобы карточка оставалась серой, а не жёлтой при старте.
   */
  statusReady: boolean;
}

// ── WebSocket message payloads ────────────────────────────────────────
export interface LineStatusPayload {
  lineName?: string;
  lineState?: number;
  shortCode?: string;
  kms?: string;
  description?: string;
  ean13?: string;
  batchNumber?: string;
  dateProduced?: string;
  datePacking?: string;
  dateExpiration?: string;
  initialCounter?: number;
  site?: string;
  itf?: string;
  capacity?: number;
  boxCount?: number;
  packageCount?: number;
  freeze?: number;
  region?: string;
  design?: number;
  printDM?: number;
}

export interface DeviceInfo {
  state?: number;
  error?: number;
  read?: number;
  unread?: number;
  batch?: string;
}

/**
 * Live-статус устройств аппарата из WS /ws/unit/{unitId}, сообщение DEVICES_STATUS.
 * Ключи — имена устройств из {@link DevicesTopology} (например, "Printer11", "CamAgregation1").
 * Такая форма позволяет масштабировать на произвольное число устройств любого типа.
 */
export interface DevicesStatusPayload {
  [deviceName: string]: DeviceInfo;
}

export interface QueueItem {
  position: number;
  shortCode: string;
  batch: string;
  dateProduced: string;
}

export interface QueuePayload {
  items: QueueItem[];
}

export interface DeviceError {
  objectName: string;
  propertyDesc: string;
  value: number;
}

export interface LogEntry {
  time: string;
  ackTime: string;
  group: string;
  description: string;
}

export interface ErrorsPayload {
  deviceErrors?: DeviceError[];
  logs?: LogEntry[];
}

// ── WebSocket alert message ───────────────────────────────────────────
export interface AlertWsMessage {
  type: 'ALERT';
  workshopId: string;
  unitId: string | number;
  severity: AlertSeverity;
  active: boolean;
  errors: unknown[];
  timestamp: string;
}

// ── WebSocket unit messages ───────────────────────────────────────────
export type UnitWsMessage =
  | { type: 'LINE_STATUS'; payload: LineStatusPayload }
  | { type: 'DEVICES_STATUS'; payload: DevicesStatusPayload }
  | { type: 'QUEUE'; payload: QueuePayload }
  | { type: 'ERRORS'; payload: ErrorsPayload };
