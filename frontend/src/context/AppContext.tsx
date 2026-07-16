import { createContext, useCallback, useContext, useMemo, useReducer } from 'react';
import type { ReactNode } from 'react';
import { DOMAIN_DEFAULTS } from '../config';
import type { AppError } from '../errors/AppError';
import type {
  AlertData,
  AlertWsMessage,
  ChangeAction,
  DevicesTopology,
  NotificationData,
  NotificationWsMessage,
  Unit,
  UnitPayload,
  UnitStatus,
  UnitTopology,
  Workshop,
  WorkshopPayload,
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

  // Production notifications (user-triggered)
  notifications: Map<string, NotificationData>;

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
  /**
   * Версия конфигурации топологии. Инкрементируется при live-изменениях,
   * чтобы DetailsLayout мог перезагрузить топологию устройств без F5.
   */
  topologyVersion: number;

  // ── Status layer (live, патчится из WS) ──
  /** workshopId → (unitId → UnitStatus) */
  unitStatusByWorkshop: Record<string, Record<string, UnitStatus>>;
}

function adjustTotalUnits(
  topology: WorkshopTopology[],
  workshopId: number,
  delta: number
): WorkshopTopology[] {
  return topology.map((w) =>
    w.id === workshopId ? { ...w, totalUnits: Math.max(0, w.totalUnits + delta) } : w
  );
}

const initialState: AppState = {
  alerts: new Map(),
  notifications: new Map(),
  headerErrors: {},
  signalStates: {
    live: 'idle',
    unit: 'idle',
  },
  workshopTopology: [],
  unitTopologyByWorkshop: {},
  devicesTopologyByUnit: {},
  topologyETag: null,
  topologyVersion: 0,
  unitStatusByWorkshop: {},
};

// ── Actions ────────────────────────────────────────────────────────────
type Action =
  | { type: 'HANDLE_ALERT'; msg: AlertWsMessage }
  | { type: 'HANDLE_NOTIFICATION'; msg: NotificationWsMessage }
  | { type: 'SET_NOTIFICATION_SNAPSHOT'; notifications: NotificationWsMessage[] }
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
  | { type: 'SET_UNIT_TOPOLOGY'; workshopId: number; topology: UnitTopology[] }
  | { type: 'SET_DEVICES_TOPOLOGY'; unitId: string; topology: DevicesTopology }
  | { type: 'SET_TOPOLOGY_ETAG'; etag: string }
  // Live admin-driven topology patches
  | { type: 'APPLY_WORKSHOP_CHANGE'; payload: WorkshopPayload; action: ChangeAction }
  | { type: 'APPLY_UNIT_CHANGE'; payload: UnitPayload; action: ChangeAction }
  | { type: 'INVALIDATE_DEVICES_TOPOLOGY'; unitId: string }
  | { type: 'BUMP_TOPOLOGY_VERSION' }
  // Live status (from WebSocket)
  | { type: 'SET_ALERT_SNAPSHOT'; alerts: AlertWsMessage[] }
  | { type: 'PATCH_UNITS_STATUS'; workshopId: number; statuses: UnitStatus[] };

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'HANDLE_ALERT': {
      const { workshopId, unitId, unitName, active, errors, timestamp } = action.msg;
      const uid = String(unitId);
      const next = new Map(state.alerts);
      if (active) next.set(uid, { unitName, errors, timestamp, workshopId });
      else next.delete(uid);
      return { ...state, alerts: next };
    }
    case 'HANDLE_NOTIFICATION': {
      const { unitId, unitName, creatorId, creatorName, active, timestamp } = action.msg;
      const uid = String(unitId);
      const next = new Map(state.notifications);
      if (active) {
        next.set(uid, {
          unitName,
          creatorId,
          creatorName: creatorName ?? null,
          eventType: action.msg.eventType ?? null,
          timestamp,
        });
      } else {
        next.delete(uid);
      }
      return { ...state, notifications: next };
    }
    case 'SET_NOTIFICATION_SNAPSHOT': {
      const next = new Map<string, NotificationData>();
      for (const msg of action.notifications) {
        if (msg.active) {
          next.set(String(msg.unitId), {
            unitName: msg.unitName,
            creatorId: msg.creatorId,
            creatorName: msg.creatorName ?? null,
            eventType: msg.eventType ?? null,
            timestamp: msg.timestamp,
          });
        }
      }
      return { ...state, notifications: next };
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
    // ── Live admin-driven topology patches ────────────────────────────
    case 'APPLY_WORKSHOP_CHANGE': {
      const wsPayload = action.payload;
      if (action.action === 'DELETE' || !wsPayload.active) {
        return {
          ...state,
          workshopTopology: state.workshopTopology.filter((w) => w.id !== wsPayload.id),
        };
      }
      const existingIndex = state.workshopTopology.findIndex((w) => w.id === wsPayload.id);
      const nextTopology = [...state.workshopTopology];
      const workshop: WorkshopTopology = {
        id: wsPayload.id,
        name: wsPayload.name ?? '',
        totalUnits: wsPayload.totalUnits,
      };
      if (existingIndex >= 0) {
        nextTopology[existingIndex] = workshop;
      } else {
        nextTopology.push(workshop);
      }
      return { ...state, workshopTopology: nextTopology };
    }
    case 'APPLY_UNIT_CHANGE': {
      const uPayload = action.payload;
      const unitId = uPayload.printsrvInstanceId ?? String(uPayload.id);
      const newWorkshopKey = String(uPayload.workshopId);

      // Найдём текущее расположение аппарата (если он уже был в другой цехе)
      let oldWorkshopKey: string | null = null;
      for (const [key, list] of Object.entries(state.unitTopologyByWorkshop)) {
        if (list.some((u) => String(u.id) === unitId)) {
          oldWorkshopKey = key;
          break;
        }
      }

      if (action.action === 'DELETE' || !uPayload.active) {
        // Удаляем неактивный/удалённый аппарат из топологии
        const nextUnitMap = { ...state.unitTopologyByWorkshop };
        const list = nextUnitMap[newWorkshopKey];
        if (list) {
          nextUnitMap[newWorkshopKey] = list.filter((u) => String(u.id) !== unitId);
        }
        // Если аппарат переехал в другой цех и стал неактивным — очистим и старый
        if (oldWorkshopKey !== null && oldWorkshopKey !== newWorkshopKey) {
          nextUnitMap[oldWorkshopKey] = nextUnitMap[oldWorkshopKey].filter(
            (u) => String(u.id) !== unitId
          );
        }

        let nextWorkshopTopology = state.workshopTopology;
        if (oldWorkshopKey !== null) {
          nextWorkshopTopology = adjustTotalUnits(nextWorkshopTopology, Number(oldWorkshopKey), -1);
        } else {
          nextWorkshopTopology = adjustTotalUnits(nextWorkshopTopology, uPayload.workshopId, -1);
        }

        return {
          ...state,
          unitTopologyByWorkshop: nextUnitMap,
          workshopTopology: nextWorkshopTopology,
        };
      }

      const unitTopology: UnitTopology = {
        id: unitId,
        workshopId: uPayload.workshopId,
        unit: uPayload.name ?? '',
      };

      let nextUnitMap = { ...state.unitTopologyByWorkshop };
      let nextWorkshopTopology = state.workshopTopology;

      if (oldWorkshopKey !== null && oldWorkshopKey !== newWorkshopKey) {
        // Переместили в другой цех
        nextUnitMap = {
          ...nextUnitMap,
          [oldWorkshopKey]: nextUnitMap[oldWorkshopKey].filter((u) => String(u.id) !== unitId),
        };
        nextWorkshopTopology = adjustTotalUnits(nextWorkshopTopology, Number(oldWorkshopKey), -1);
        nextWorkshopTopology = adjustTotalUnits(nextWorkshopTopology, uPayload.workshopId, +1);
      }

      const newList = [...(nextUnitMap[newWorkshopKey] ?? [])];
      const existingUnitIndex = newList.findIndex((u) => String(u.id) === unitId);
      if (existingUnitIndex >= 0) {
        newList[existingUnitIndex] = unitTopology;
      } else {
        newList.push(unitTopology);
        if (action.action === 'CREATE') {
          nextWorkshopTopology = adjustTotalUnits(nextWorkshopTopology, uPayload.workshopId, +1);
        }
      }
      nextUnitMap[newWorkshopKey] = newList;

      return {
        ...state,
        unitTopologyByWorkshop: nextUnitMap,
        workshopTopology: nextWorkshopTopology,
      };
    }
    case 'INVALIDATE_DEVICES_TOPOLOGY': {
      if (!state.devicesTopologyByUnit[action.unitId]) return state;
      const nextDevices = { ...state.devicesTopologyByUnit };
      delete nextDevices[action.unitId];
      return {
        ...state,
        devicesTopologyByUnit: nextDevices,
        topologyVersion: state.topologyVersion + 1,
      };
    }
    case 'BUMP_TOPOLOGY_VERSION':
      return { ...state, topologyVersion: state.topologyVersion + 1 };
    // ── Live status ───────────────────────────────────────────────────
    case 'SET_ALERT_SNAPSHOT': {
      // Начальный срез алёртов при подключении к /ws/live.
      // Полностью заменяет текущую карту алёртов.
      const next = new Map<string, AlertData>();
      for (const msg of action.alerts) {
        if (msg.active) {
          next.set(String(msg.unitId), {
            unitName: msg.unitName,
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
  /** Топология аппаратов по цехам (для вычисления warning-статуса цехов). */
  unitTopologyByWorkshop: Record<string, UnitTopology[]>;
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
  setUnitTopology: (workshopId: number, topology: UnitTopology[]) => void;
  /** Сохраняет топологию устройств аппарата (принтеры, камеры). */
  setDevicesTopology: (unitId: string, topology: DevicesTopology) => void;
  /** Сохраняет ETag, полученный от topology-эндпоинтов. */
  setTopologyETag: (etag: string) => void;
  /** Патчит топологию цеха по WS-сообщению об изменении. */
  applyWorkshopChange: (payload: WorkshopPayload, action: ChangeAction) => void;
  /** Патчит топологию автомата по WS-сообщению об изменении. */
  applyUnitChange: (payload: UnitPayload, action: ChangeAction) => void;
  /** Инвалидирует кэш топологии устройств аппарата. */
  invalidateDevicesTopology: (unitId: string) => void;
  /** Инкрементирует версию топологии для принудительного перезапроса. */
  bumpTopologyVersion: () => void;
  // Status actions (вызываются из WS-хуков)
  /** Применяет начальный снапшот алёртов, полученный при подключении к /ws/live */
  setAlertSnapshot: (alerts: AlertWsMessage[]) => void;
  patchUnitsStatus: (workshopId: number, statuses: UnitStatus[]) => void;
  // Notifications (user-triggered production notifications)
  handleNotification: (msg: NotificationWsMessage) => void;
  setNotificationSnapshot: (notifications: NotificationWsMessage[]) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  // ── Actions ─────────────────────────────────────────────────────────
  const handleAlert = useCallback((msg: AlertWsMessage) => {
    dispatch({ type: 'HANDLE_ALERT', msg });
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
    (workshopId: number, topology: UnitTopology[]) =>
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
  const applyWorkshopChange = useCallback(
    (payload: WorkshopPayload, action: ChangeAction) =>
      dispatch({ type: 'APPLY_WORKSHOP_CHANGE', payload, action }),
    []
  );
  const applyUnitChange = useCallback(
    (payload: UnitPayload, action: ChangeAction) =>
      dispatch({ type: 'APPLY_UNIT_CHANGE', payload, action }),
    []
  );
  const invalidateDevicesTopology = useCallback(
    (unitId: string) => dispatch({ type: 'INVALIDATE_DEVICES_TOPOLOGY', unitId }),
    []
  );
  const bumpTopologyVersion = useCallback(() => dispatch({ type: 'BUMP_TOPOLOGY_VERSION' }), []);
  const setAlertSnapshot = useCallback(
    (alerts: AlertWsMessage[]) => dispatch({ type: 'SET_ALERT_SNAPSHOT', alerts }),
    []
  );
  const patchUnitsStatus = useCallback(
    (workshopId: number, statuses: UnitStatus[]) =>
      dispatch({ type: 'PATCH_UNITS_STATUS', workshopId, statuses }),
    []
  );
  const handleNotification = useCallback((msg: NotificationWsMessage) => {
    dispatch({ type: 'HANDLE_NOTIFICATION', msg });
  }, []);
  const setNotificationSnapshot = useCallback(
    (notifications: NotificationWsMessage[]) =>
      dispatch({ type: 'SET_NOTIFICATION_SNAPSHOT', notifications }),
    []
  );

  // ── Computed values (topology + status → UI types) ───────────────────
  // problemUnits вычисляется из глобального стейта алёртов — сервер больше
  // не шлёт отдельный WORKSHOPS_STATUS, клиент считает сам.
  const workshops = useMemo<Workshop[]>(
    () =>
      state.workshopTopology.map((t) => {
        const units = state.unitTopologyByWorkshop[t.id] ?? [];
        const unitIds = new Set(units.map((u) => String(u.id)));
        const notificationCount = [...state.notifications.entries()].filter(([unitId]) =>
          unitIds.has(unitId)
        ).length;
        return {
          id: t.id,
          name: t.name,
          totalUnits: t.totalUnits,
          problemUnits: [...state.alerts.values()].filter((a) => a.workshopId === t.id).length,
          notificationUnits: notificationCount,
        };
      }),
    [state.workshopTopology, state.alerts, state.notifications, state.unitTopologyByWorkshop]
  );

  const unitsByWorkshop = useMemo<Record<string, Unit[]>>(() => {
    const result: Record<string, Unit[]> = {};
    for (const [workshopId, topologies] of Object.entries(state.unitTopologyByWorkshop)) {
      const statusMap = state.unitStatusByWorkshop[workshopId] ?? {};
      result[workshopId] = topologies.map((t) => {
        const status = statusMap[t.id];
        return {
          id: t.id,
          workshopId: t.workshopId,
          unit: t.unit,
          event: status?.event ?? DOMAIN_DEFAULTS.noDataEvent,
          // statusReady = false пока UNITS_STATUS от WS ещё не пришёл для этого аппарата.
          // Позволяет UnitCard показывать серый цвет вместо жёлтого при старте.
          statusReady: t.id in statusMap,
          cameraRead: status?.cameraRead ?? null,
          cameraUnread: status?.cameraUnread ?? null,
        };
      });
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
        unitTopologyByWorkshop: state.unitTopologyByWorkshop,
        handleAlert,
        setSignalState,
        setHeaderError,
        clearHeaderError,
        setWorkshopTopology,
        setUnitTopology,
        setDevicesTopology,
        setTopologyETag,
        applyWorkshopChange,
        applyUnitChange,
        invalidateDevicesTopology,
        bumpTopologyVersion,
        setAlertSnapshot,
        patchUnitsStatus,
        handleNotification,
        setNotificationSnapshot,
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
