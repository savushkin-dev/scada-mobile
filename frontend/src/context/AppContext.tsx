import { createContext, useCallback, useContext, useMemo, useReducer } from 'react';
import type { ReactNode } from 'react';
import { ALERT_VIBRATION_PATTERN, DOMAIN_DEFAULTS } from '../config';
import type {
  AlertData,
  AlertWsMessage,
  Unit,
  UnitStatus,
  UnitTopology,
  Workshop,
  WorkshopTopology,
} from '../types';

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

  // ── Topology layer (статика, один раз) ──
  workshopTopology: WorkshopTopology[];
  unitTopologyByWorkshop: Record<string, UnitTopology[]>;
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
  workshopTopology: [],
  unitTopologyByWorkshop: {},
  topologyETag: null,
  unitStatusByWorkshop: {},
};

// ── Actions ────────────────────────────────────────────────────────────
type Action =
  | { type: 'HANDLE_ALERT'; msg: AlertWsMessage }
  // Topology
  | { type: 'SET_WORKSHOP_TOPOLOGY'; topology: WorkshopTopology[] }
  | { type: 'SET_UNIT_TOPOLOGY'; workshopId: string; topology: UnitTopology[] }
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
  // Alerts
  handleAlert: (msg: AlertWsMessage) => void;
  // Topology actions (вызываются один раз при загрузке)
  setWorkshopTopology: (topology: WorkshopTopology[]) => void;
  setUnitTopology: (workshopId: string, topology: UnitTopology[]) => void;
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
  const setWorkshopTopology = useCallback(
    (topology: WorkshopTopology[]) => dispatch({ type: 'SET_WORKSHOP_TOPOLOGY', topology }),
    []
  );
  const setUnitTopology = useCallback(
    (workshopId: string, topology: UnitTopology[]) =>
      dispatch({ type: 'SET_UNIT_TOPOLOGY', workshopId, topology }),
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

  return (
    <AppContext.Provider
      value={{
        state,
        workshops,
        unitsByWorkshop,
        handleAlert,
        setWorkshopTopology,
        setUnitTopology,
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
