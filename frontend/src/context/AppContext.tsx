import { createContext, useCallback, useContext, useMemo, useReducer } from 'react';
import type { ReactNode } from 'react';
import type {
  AlertData,
  AlertWsMessage,
  ScreenId,
  TabId,
  Unit,
  UnitStatus,
  UnitTopology,
  Workshop,
  WorkshopStatus,
  WorkshopTopology,
} from '../types';

// ── Constants ──────────────────────────────────────────────────────────
const ALERT_VIBRATION_PATTERN = [200, 100, 200];

// ── State ──────────────────────────────────────────────────────────────

/**
 * Состояние приложения разделено на два слоя:
 *
 * **Topology** — статические данные конфигурации, загружаются один раз при старте.
 * Хранятся бессрочно в памяти на время сессии.
 *
 * **Status** — live-данные, обновляются через WebSocket после каждого scan cycle.
 * Патчатся поверх topology для формирования итоговых `Workshop[]` / `Unit[]`.
 */
export interface AppState {
  // Navigation
  screen: ScreenId;
  activeTab: TabId;
  currentWorkshopId: string | null;
  currentWorkshopName: string | null;
  currentUnitId: string | null;

  // Alerts
  alerts: Map<string, AlertData>;

  // ── Topology layer (статика, один раз) ──
  workshopTopology: WorkshopTopology[];
  unitTopologyByWorkshop: Record<string, UnitTopology[]>;

  // ── Status layer (live, патчится из WS) ──
  /** workshopId → problemUnits */
  workshopStatus: Record<string, number>;
  /** workshopId → (unitId → UnitStatus) */
  unitStatusByWorkshop: Record<string, Record<string, UnitStatus>>;
}

const initialState: AppState = {
  screen: 'dashboard',
  activeTab: 'tab-batch',
  currentWorkshopId: null,
  currentWorkshopName: null,
  currentUnitId: null,
  alerts: new Map(),
  workshopTopology: [],
  unitTopologyByWorkshop: {},
  workshopStatus: {},
  unitStatusByWorkshop: {},
};

// ── Actions ────────────────────────────────────────────────────────────
type Action =
  | { type: 'NAVIGATE'; screen: ScreenId }
  | { type: 'ACTIVATE_TAB'; tab: TabId }
  | { type: 'HANDLE_ALERT'; msg: AlertWsMessage }
  | { type: 'SET_CURRENT_WORKSHOP'; workshopId: string; workshopName: string }
  | { type: 'SET_CURRENT_UNIT'; unitId: string; workshopId: string }
  // Topology
  | { type: 'SET_WORKSHOP_TOPOLOGY'; topology: WorkshopTopology[] }
  | { type: 'SET_UNIT_TOPOLOGY'; workshopId: string; topology: UnitTopology[] }
  // Live status (from WebSocket)
  | { type: 'PATCH_WORKSHOPS_STATUS'; statuses: WorkshopStatus[] }
  | { type: 'PATCH_UNITS_STATUS'; workshopId: string; statuses: UnitStatus[] };

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'NAVIGATE':
      return { ...state, screen: action.screen };
    case 'ACTIVATE_TAB':
      return { ...state, activeTab: action.tab };
    case 'SET_CURRENT_WORKSHOP':
      return {
        ...state,
        currentWorkshopId: action.workshopId,
        currentWorkshopName: action.workshopName,
      };
    case 'SET_CURRENT_UNIT':
      return { ...state, currentUnitId: action.unitId, currentWorkshopId: action.workshopId };
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
    // ── Live status ───────────────────────────────────────────────────
    case 'PATCH_WORKSHOPS_STATUS': {
      const next = { ...state.workshopStatus };
      for (const s of action.statuses) next[s.workshopId] = s.problemUnits;
      return { ...state, workshopStatus: next };
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
  // Navigation
  navigate: (screen: ScreenId) => void;
  navigateToWorkshop: (workshopId: string, workshopName: string) => void;
  activateTab: (tab: TabId) => void;
  openDetails: (workshopId: string, unitId: string) => void;
  // Alerts
  handleAlert: (msg: AlertWsMessage) => void;
  // Topology actions (вызываются один раз при загрузке)
  setWorkshopTopology: (topology: WorkshopTopology[]) => void;
  setUnitTopology: (workshopId: string, topology: UnitTopology[]) => void;
  // Status actions (вызываются из WS-хуков)
  patchWorkshopsStatus: (statuses: WorkshopStatus[]) => void;
  patchUnitsStatus: (workshopId: string, statuses: UnitStatus[]) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  // ── Actions ─────────────────────────────────────────────────────────
  const navigate = useCallback((screen: ScreenId) => dispatch({ type: 'NAVIGATE', screen }), []);
  const activateTab = useCallback((tab: TabId) => dispatch({ type: 'ACTIVATE_TAB', tab }), []);
  const handleAlert = useCallback((msg: AlertWsMessage) => {
    dispatch({ type: 'HANDLE_ALERT', msg });
    if (document.visibilityState === 'visible' && navigator.vibrate) {
      navigator.vibrate(ALERT_VIBRATION_PATTERN);
    }
  }, []);
  const navigateToWorkshop = useCallback((workshopId: string, workshopName: string) => {
    dispatch({ type: 'SET_CURRENT_WORKSHOP', workshopId, workshopName });
    dispatch({ type: 'NAVIGATE', screen: 'workshop' });
  }, []);
  const openDetails = useCallback((workshopId: string, unitId: string) => {
    dispatch({ type: 'SET_CURRENT_UNIT', unitId, workshopId });
    dispatch({ type: 'ACTIVATE_TAB', tab: 'tab-batch' });
    dispatch({ type: 'NAVIGATE', screen: 'details' });
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
  const patchWorkshopsStatus = useCallback(
    (statuses: WorkshopStatus[]) => dispatch({ type: 'PATCH_WORKSHOPS_STATUS', statuses }),
    []
  );
  const patchUnitsStatus = useCallback(
    (workshopId: string, statuses: UnitStatus[]) =>
      dispatch({ type: 'PATCH_UNITS_STATUS', workshopId, statuses }),
    []
  );

  // ── Computed values (topology + status → UI types) ───────────────────
  const workshops = useMemo<Workshop[]>(
    () =>
      state.workshopTopology.map((t) => ({
        id: t.id,
        name: t.name,
        totalUnits: t.totalUnits,
        problemUnits: state.workshopStatus[t.id] ?? 0,
      })),
    [state.workshopTopology, state.workshopStatus]
  );

  const unitsByWorkshop = useMemo<Record<string, Unit[]>>(() => {
    const result: Record<string, Unit[]> = {};
    for (const [workshopId, topologies] of Object.entries(state.unitTopologyByWorkshop)) {
      const statusMap = state.unitStatusByWorkshop[workshopId] ?? {};
      result[workshopId] = topologies.map((t) => ({
        id: t.id,
        workshopId: t.workshopId,
        unit: t.unit,
        event: statusMap[t.id]?.event ?? 'Нет данных',
        timer: statusMap[t.id]?.timer ?? '00:00:00',
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
        navigate,
        navigateToWorkshop,
        activateTab,
        openDetails,
        handleAlert,
        setWorkshopTopology,
        setUnitTopology,
        patchWorkshopsStatus,
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
