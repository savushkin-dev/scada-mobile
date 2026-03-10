import { createContext, useCallback, useContext, useMemo, useReducer } from 'react';
import type { ReactNode } from 'react';
import { ALERT_VIBRATION_PATTERN, DOMAIN_DEFAULTS } from '../config';
import type { AppError } from '../errors/AppError';
import type {
  AlertData,
  AlertWsMessage,
  DevicesTopology,
  Unit,
  UnitStatus,
  UnitTopology,
  Workshop,
  WorkshopTopology,
} from '../types';

export type HeaderErrorSlot = 'topology' | 'live' | 'unit';
export type SignalSlot = 'live' | 'unit';
/**
 * Состояние WS-соединения.
 *
 * - `'idle'`         — начальное состояние, соединение ещё не установлено.
 * - `'connected'`    — соединение активно, данные поступают в штатном режиме.
 * - `'reconnecting'` — соединение потеряно, выполняются попытки 1…N-1;
 *                    показывай skeleton-заглушку, но не ошибку в шапке.
 * - `'error'`        — исчерпан порог попыток; показывай ошибку в шапке
 *                    и текстовое сообщение вместо skeleton.
 */
export type SignalState = 'idle' | 'connected' | 'reconnecting' | 'error';

export interface HeaderErrorEntry {
  slot: HeaderErrorSlot;
  error: AppError;
  updatedAt: number;
  retryAction?: (() => void) | undefined;
}

// ── State ──────────────────────────────────────────────────────────────

/**
 * Состояние приложения разделено на два слоя:
 *
 * **Topology** — статические данные конфигурации, загружаются один раз при старте.
 * Хранятся бессрочно в памяти на время сессии.
 *
 * **Status** — live-данные, обновляются через WebSocket после каждого scan cycle.
 * Патчатся поверх topology для формирования итоговых `Workshop[]` / `Unit[]`.
 *
 * Навигационное состояние (текущий экран, текущий цех/аппарат, активный таб)
 * намеренно отсутствует здесь — оно хранится в URL через React Router.
 */
export interface AppState {
  // Alerts
  alerts: Map<string, AlertData>;

  // UI-ошибки, отображаемые единообразно в шапке.
  headerErrors: Partial<Record<HeaderErrorSlot, HeaderErrorEntry>>;

  // Состояние live-каналов для управления деградацией UI.
  signalStates: Record<SignalSlot, SignalState>;

  // ── Topology layer (статика, один раз) ──
  workshopTopology: WorkshopTopology[];
  unitTopologyByWorkshop: Record<string, UnitTopology[]>;
  /**
   * Статическая топология устройств по `unitId`.
   * Загружается один раз при первом входе на экран деталей аппарата.
   * Ключ — `unitId`.
   */
  devicesTopologyByUnit: Record<string, DevicesTopology>;
  /**
   * ETag, полученный от сервера при последней успешной загрузке topology.
   * Одинаков для обоих topology-эндпоинтов (хэш общей конфигурации).
   * Передаётся обратно в заголовке `If-None-Match` при повторных запросах.
   */
  topologyETag: string | null;

  // ── Status layer (live, патчится из WS) ──
  /** workshopId → (unitId → UnitStatus) */
  unitStatusByWorkshop: Record<string, Record<string, UnitStatus>>;
}

const initialState: AppState = {
  alerts: new Map(),
  headerErrors: {},
  signalStates: {
    live: 'idle',
    unit: 'idle',
  },
  workshopTopology: [],
  unitTopologyByWorkshop: {},
  devicesTopologyByUnit: {},
  topologyETag: null,
  unitStatusByWorkshop: {},
};

// ── Actions ────────────────────────────────────────────────────────────
type Action =
  | { type: 'HANDLE_ALERT'; msg: AlertWsMessage }
  | {
      type: 'SET_HEADER_ERROR';
      slot: HeaderErrorSlot;
      error: AppError;
      retryAction?: (() => void) | undefined;
    }
  | { type: 'CLEAR_HEADER_ERROR'; slot: HeaderErrorSlot }
  | { type: 'SET_SIGNAL_STATE'; slot: SignalSlot; signalState: SignalState }
  // Topology
  | { type: 'SET_WORKSHOP_TOPOLOGY'; topology: WorkshopTopology[] }
  | { type: 'SET_UNIT_TOPOLOGY'; workshopId: string; topology: UnitTopology[] }
  | { type: 'SET_DEVICES_TOPOLOGY'; unitId: string; topology: DevicesTopology }
  | { type: 'SET_TOPOLOGY_ETAG'; etag: string }
  // Live status (from WebSocket)
  | { type: 'SET_ALERT_SNAPSHOT'; alerts: AlertWsMessage[] }
  | { type: 'PATCH_UNITS_STATUS'; workshopId: string; statuses: UnitStatus[] };

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'HANDLE_ALERT': {
      const { workshopId, unitId, severity, active, errors, timestamp } = action.msg;
      const uid = String(unitId);
      const next = new Map(state.alerts);
      if (active) next.set(uid, { severity, errors, timestamp, workshopId });
      else next.delete(uid);
      return { ...state, alerts: next };
    }
    case 'SET_HEADER_ERROR': {
      return {
        ...state,
        headerErrors: {
          ...state.headerErrors,
          [action.slot]: {
            slot: action.slot,
            error: action.error,
            updatedAt: Date.now(),
            retryAction: action.retryAction,
          },
        },
      };
    }
    case 'CLEAR_HEADER_ERROR': {
      // Guard: если слот уже пустой — не создаём новый объект.
      if (!(action.slot in state.headerErrors)) return state;
      const nextErrors = { ...state.headerErrors };
      delete nextErrors[action.slot];
      return { ...state, headerErrors: nextErrors };
    }
    case 'SET_SIGNAL_STATE': {
      // Guard: если состояние не изменилось — не вызываем перерендер;
      // важно при частых вызовах из onRecovered через WS-сообщения.
      if (state.signalStates[action.slot] === action.signalState) return state;
      return {
        ...state,
        signalStates: {
          ...state.signalStates,
          [action.slot]: action.signalState,
        },
      };
    }
    // ── Topology ─────────────────────────────────────────────────────
    case 'SET_WORKSHOP_TOPOLOGY':
      return { ...state, workshopTopology: action.topology };
    case 'SET_UNIT_TOPOLOGY':
      return {
        ...state,
        unitTopologyByWorkshop: {
          ...state.unitTopologyByWorkshop,
          [action.workshopId]: action.topology,
        },
      };
    case 'SET_DEVICES_TOPOLOGY':
      // Guard: если топология для this unitId уже идентична — не обновляем
      // (при 304 мы не вызываем этот action, но на всякий случай)
      if (state.devicesTopologyByUnit[action.unitId] === action.topology) return state;
      return {
        ...state,
        devicesTopologyByUnit: {
          ...state.devicesTopologyByUnit,
          [action.unitId]: action.topology,
        },
      };
    case 'SET_TOPOLOGY_ETAG':
      return { ...state, topologyETag: action.etag };
    // ── Live status ───────────────────────────────────────────────────
    case 'SET_ALERT_SNAPSHOT': {
      // Начальный срез алёртов при подключении к /ws/live.
      // Полностью заменяет текущую карту алёртов.
      const next = new Map<string, AlertData>();
      for (const msg of action.alerts) {
        if (msg.active) {
          next.set(String(msg.unitId), {
            severity: msg.severity,
            errors: msg.errors,
            timestamp: msg.timestamp,
            workshopId: msg.workshopId,
          });
        }
      }
      return { ...state, alerts: next };
    }
    case 'PATCH_UNITS_STATUS': {
      const statusMap: Record<string, UnitStatus> = {
        ...(state.unitStatusByWorkshop[action.workshopId] ?? {}),
      };
      for (const s of action.statuses) statusMap[s.unitId] = s;
      return {
        ...state,
        unitStatusByWorkshop: {
          ...state.unitStatusByWorkshop,
          [action.workshopId]: statusMap,
        },
      };
    }
    default:
      return state;
  }
}

// ── Context ────────────────────────────────────────────────────────────
interface AppContextValue {
  state: AppState;
  // Computed (topology merged with status — для компонентов UI)
  workshops: Workshop[];
  unitsByWorkshop: Record<string, Unit[]>;
  headerError: HeaderErrorEntry | null;
  // Alerts
  handleAlert: (msg: AlertWsMessage) => void;
  setSignalState: (slot: SignalSlot, signalState: SignalState) => void;
  setHeaderError: (
    slot: HeaderErrorSlot,
    error: AppError,
    retryAction?: (() => void) | undefined
  ) => void;
  clearHeaderError: (slot: HeaderErrorSlot) => void;
  // Topology actions (вызываются один раз при загрузке)
  setWorkshopTopology: (topology: WorkshopTopology[]) => void;
  setUnitTopology: (workshopId: string, topology: UnitTopology[]) => void;
  /** Сохраняет топологию устройств аппарата (принтеры, камеры). */
  setDevicesTopology: (unitId: string, topology: DevicesTopology) => void;
  /** Сохраняет ETag, полученный от topology-эндпоинтов. */
  setTopologyETag: (etag: string) => void;
  // Status actions (вызываются из WS-хуков)
  /** Применяет начальный снапшот алёртов, полученный при подключении к /ws/live */
  setAlertSnapshot: (alerts: AlertWsMessage[]) => void;
  patchUnitsStatus: (workshopId: string, statuses: UnitStatus[]) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  // ── Actions ─────────────────────────────────────────────────────────
  const handleAlert = useCallback((msg: AlertWsMessage) => {
    dispatch({ type: 'HANDLE_ALERT', msg });
    if (document.visibilityState === 'visible' && navigator.vibrate) {
      navigator.vibrate(ALERT_VIBRATION_PATTERN);
    }
  }, []);
  const setSignalState = useCallback(
    (slot: SignalSlot, signalState: SignalState) =>
      dispatch({ type: 'SET_SIGNAL_STATE', slot, signalState }),
    []
  );
  const setHeaderError = useCallback(
    (slot: HeaderErrorSlot, error: AppError, retryAction?: (() => void) | undefined) =>
      dispatch({ type: 'SET_HEADER_ERROR', slot, error, retryAction }),
    []
  );
  const clearHeaderError = useCallback(
    (slot: HeaderErrorSlot) => dispatch({ type: 'CLEAR_HEADER_ERROR', slot }),
    []
  );
  const setWorkshopTopology = useCallback(
    (topology: WorkshopTopology[]) => dispatch({ type: 'SET_WORKSHOP_TOPOLOGY', topology }),
    []
  );
  const setUnitTopology = useCallback(
    (workshopId: string, topology: UnitTopology[]) =>
      dispatch({ type: 'SET_UNIT_TOPOLOGY', workshopId, topology }),
    []
  );
  const setDevicesTopology = useCallback(
    (unitId: string, topology: DevicesTopology) =>
      dispatch({ type: 'SET_DEVICES_TOPOLOGY', unitId, topology }),
    []
  );
  const setTopologyETag = useCallback(
    (etag: string) => dispatch({ type: 'SET_TOPOLOGY_ETAG', etag }),
    []
  );
  const setAlertSnapshot = useCallback(
    (alerts: AlertWsMessage[]) => dispatch({ type: 'SET_ALERT_SNAPSHOT', alerts }),
    []
  );
  const patchUnitsStatus = useCallback(
    (workshopId: string, statuses: UnitStatus[]) =>
      dispatch({ type: 'PATCH_UNITS_STATUS', workshopId, statuses }),
    []
  );

  // ── Computed values (topology + status → UI types) ───────────────────
  // problemUnits вычисляется из глобального стейта алёртов — сервер больше
  // не шлёт отдельный WORKSHOPS_STATUS, клиент считает сам.
  const workshops = useMemo<Workshop[]>(
    () =>
      state.workshopTopology.map((t) => ({
        id: t.id,
        name: t.name,
        totalUnits: t.totalUnits,
        problemUnits: [...state.alerts.values()].filter((a) => a.workshopId === t.id).length,
      })),
    [state.workshopTopology, state.alerts]
  );

  const unitsByWorkshop = useMemo<Record<string, Unit[]>>(() => {
    const result: Record<string, Unit[]> = {};
    for (const [workshopId, topologies] of Object.entries(state.unitTopologyByWorkshop)) {
      const statusMap = state.unitStatusByWorkshop[workshopId] ?? {};
      result[workshopId] = topologies.map((t) => ({
        id: t.id,
        workshopId: t.workshopId,
        unit: t.unit,
        event: statusMap[t.id]?.event ?? DOMAIN_DEFAULTS.noDataEvent,
        timer: statusMap[t.id]?.timer ?? DOMAIN_DEFAULTS.zeroTimer,
        // statusReady = false пока UNITS_STATUS от WS ещё не пришёл для этого аппарата.
        // Позволяет UnitCard показывать серый цвет вместо жёлтого при старте.
        statusReady: t.id in statusMap,
      }));
    }
    return result;
  }, [state.unitTopologyByWorkshop, state.unitStatusByWorkshop]);

  const headerError = useMemo<HeaderErrorEntry | null>(
    () => state.headerErrors.unit ?? state.headerErrors.live ?? state.headerErrors.topology ?? null,
    [state.headerErrors]
  );

  return (
    <AppContext.Provider
      value={{
        state,
        workshops,
        unitsByWorkshop,
        headerError,
        handleAlert,
        setSignalState,
        setHeaderError,
        clearHeaderError,
        setWorkshopTopology,
        setUnitTopology,
        setDevicesTopology,
        setTopologyETag,
        setAlertSnapshot,
        patchUnitsStatus,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAppContext(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useAppContext must be used within AppProvider');
  return ctx;
}
