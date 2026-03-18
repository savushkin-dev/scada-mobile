/**
 * types/index.ts — публичный TypeScript API доменных типов.
 *
 * Внешние типы (данные из REST API и WebSocket) ВЫВОДЯТСЯ из Zod-схем
 * через z.infer<> — без дублирования определений.
 *
 * Внутренние типы (UI-слой, мёрдж-виды, нормализованные данные)
 * объявлены здесь явно.
 */

// Импорт для использования внутри этого файла (AlertData.errors)
import type { AlertError } from '../schemas';

// ── Типы, выведенные из Zod-схем (единственный источник правды) ───────

// topology (REST API)
export type { WorkshopTopology, UnitTopology, DevicesTopology } from '../schemas';

// live WebSocket (/ws/live)
export type {
  AlertError,
  AlertWsMessage,
  AlertSnapshotMessage,
  UnitsStatusMessage,
  UnitStatus,
  LiveWsIncomingMessage,
} from '../schemas';

// unit WebSocket (/ws/unit/{unitId})
export type {
  UnitWsMessage,
  LineStatusPayload,
  DevicesStatusWsPrinter,
  DevicesStatusWsCamera,
  DevicesStatusWsPayload,
  QueueItem,
  QueuePayload,
  DeviceError,
  LogEntry,
  ErrorsPayload,
} from '../schemas';

// ── Navigation ────────────────────────────────────────────────────────

/** URL search-param «?tab=» для детальной страницы аппарата. */
export type TabId = 'tab-batch' | 'tab-devices' | 'tab-queue' | 'tab-logs';

// ── Доменная модель (внутренняя, только UI) ───────────────────────────

export interface AlertData {
  /** Читаемое название аппарата (из AlertMessageDTO.unitName). */
  unitName: string;
  /** Детализированные ошибки аппарата. */
  errors: AlertError[];
  timestamp: string;
  workshopId: string;
}

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
  /**
   * `true` — WS-статус для этого аппарата уже получен.
   * `false` — топология загружена, но первый UNITS_STATUS ещё не пришёл.
   *
   * Используется в {@link getUnitStatusLevel} для возврата `'pending'`,
   * чтобы карточка оставалась в нейтральном состоянии до первого live-статуса.
   */
  statusReady: boolean;
}

// ── Внутренняя нормализованная форма устройств (не WS wire format) ────

export interface DeviceInfo {
  state?: number;
  error?: number;
  read?: number;
  unread?: number;
  batch?: string;
}

/**
 * Нормализованная карта устройств для отображения.
 * Ключи — имена устройств (например, "Printer11").
 * Производится из {@link DevicesStatusWsPayload} в DetailsLayout.
 */
export interface DevicesStatusPayload {
  [deviceName: string]: DeviceInfo;
}

// ── WebSocket client → server ─────────────────────────────────────────

/** Действия, отправляемые клиентом на сервер через /ws/live */
export type LiveWsClientAction =
  | { action: 'SUBSCRIBE_WORKSHOP'; workshopId: string }
  | { action: 'UNSUBSCRIBE_WORKSHOP' };
