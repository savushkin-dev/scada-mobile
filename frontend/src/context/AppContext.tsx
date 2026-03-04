import { createContext, useCallback, useContext, useReducer } from 'react';
import type { ReactNode } from 'react';
import type { AlertData, AlertWsMessage, ScreenId, TabId, Unit, Workshop } from '../types';

// ── Constants ──────────────────────────────────────────────────────────
const ALERT_VIBRATION_PATTERN = [200, 100, 200];

// ── State ──────────────────────────────────────────────────────────────
export interface AppState {
  screen: ScreenId;
  activeTab: TabId;
  workshops: Workshop[];
  unitsByWorkshop: Record<string, Unit[]>;
  alerts: Map<string, AlertData>;
  currentWorkshopId: string | null;
  currentWorkshopName: string | null;
  currentUnitId: string | null;
}

const initialState: AppState = {
  screen: 'dashboard',
  activeTab: 'tab-batch',
  workshops: [],
  unitsByWorkshop: {},
  alerts: new Map(),
  currentWorkshopId: null,
  currentWorkshopName: null,
  currentUnitId: null,
};

// ── Actions ────────────────────────────────────────────────────────────
type Action =
  | { type: 'NAVIGATE'; screen: ScreenId }
  | { type: 'ACTIVATE_TAB'; tab: TabId }
  | { type: 'SET_WORKSHOPS'; workshops: Workshop[] }
  | { type: 'SET_UNITS'; workshopId: string; units: Unit[] }
  | { type: 'HANDLE_ALERT'; msg: AlertWsMessage }
  | { type: 'SET_CURRENT_WORKSHOP'; workshopId: string; workshopName: string }
  | { type: 'SET_CURRENT_UNIT'; unitId: string; workshopId: string };

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'NAVIGATE':
      return { ...state, screen: action.screen };
    case 'ACTIVATE_TAB':
      return { ...state, activeTab: action.tab };
    case 'SET_WORKSHOPS':
      return { ...state, workshops: action.workshops };
    case 'SET_UNITS':
      return { ...state, unitsByWorkshop: { ...state.unitsByWorkshop, [action.workshopId]: action.units } };
    case 'HANDLE_ALERT': {
      const { workshopId, unitId, severity, active, errors, timestamp } = action.msg;
      const uid = String(unitId);
      const next = new Map(state.alerts);
      if (active) next.set(uid, { severity, errors, timestamp, workshopId });
      else next.delete(uid);
      return { ...state, alerts: next };
    }
    case 'SET_CURRENT_WORKSHOP':
      return { ...state, currentWorkshopId: action.workshopId, currentWorkshopName: action.workshopName };
    case 'SET_CURRENT_UNIT':
      return { ...state, currentUnitId: action.unitId, currentWorkshopId: action.workshopId };
    default:
      return state;
  }
}

// ── Context ────────────────────────────────────────────────────────────
interface AppContextValue {
  state: AppState;
  navigate: (screen: ScreenId) => void;
  navigateToWorkshop: (workshopId: string, workshopName: string) => void;
  activateTab: (tab: TabId) => void;
  setWorkshops: (workshops: Workshop[]) => void;
  setUnits: (workshopId: string, units: Unit[]) => void;
  handleAlert: (msg: AlertWsMessage) => void;
  openDetails: (workshopId: string, unitId: string) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const navigate = useCallback((screen: ScreenId) => dispatch({ type: 'NAVIGATE', screen }), []);
  const activateTab = useCallback((tab: TabId) => dispatch({ type: 'ACTIVATE_TAB', tab }), []);
  const setWorkshops = useCallback(
    (workshops: Workshop[]) => dispatch({ type: 'SET_WORKSHOPS', workshops }),
    []
  );
  const setUnits = useCallback(
    (workshopId: string, units: Unit[]) => dispatch({ type: 'SET_UNITS', workshopId, units }),
    []
  );
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

  return (
    <AppContext.Provider
      value={{ state, navigate, navigateToWorkshop, activateTab, setWorkshops, setUnits, handleAlert, openDetails }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useAppContext(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useAppContext must be used within AppProvider');
  return ctx;
}
