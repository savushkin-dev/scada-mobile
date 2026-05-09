import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  DEFAULT_DETAIL_TAB,
  DETAILS_PAGE_STYLE,
  DETAILS_SCROLL_SECTION_STYLE,
  DOMAIN_DEFAULTS,
  DOMAIN_FLAGS,
  TAB_ROUTE_SEGMENT,
  ROUTE_SEGMENT_TAB,
} from '../config';
import { BottomNav } from '../components/BottomNav';
import { Fab } from '../components/Fab';
import { DetailsProvider } from '../context/DetailsContext';
import { usePageHeader } from '../context/PageHeaderContext';
import {
  fetchDevicesTopology,
  fetchUnitsTopology,
  fetchWorkshopsTopology,
  type TopologyFetchResult,
} from '../api/workshops';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import { usePageError } from '../hooks/usePageError';
import { useHeaderErrorSlot } from '../hooks/useHeaderErrorSlot';
import { useUnitWs } from '../hooks/useUnitWs';
import type {
  DevicesStatusPayload,
  DevicesStatusWsPayload,
  DevicesTopology,
  ErrorsPayload,
  LineStatusPayload,
  QueuePayload,
  TabId,
  UnitTopology,
  UnitWsMessage,
  WorkshopTopology,
} from '../types';

/**
 * Нормализует payload сообщения DEVICES_STATUS из wire-формата бекенда
 * (группы с массивами, числовые поля — строки) в плоский словарь DevicesStatusPayload
 * (ключ — имя устройства, значения — числа), используемый компонентами отображения.
 *
 * Преобразование строк в числа необходимо для сравнения с DOMAIN_FLAGS.active (= 1)
 * в getDeviceStatusLevel.
 */
function normalizeDevicesStatus(raw: DevicesStatusWsPayload): DevicesStatusPayload {
  const result: DevicesStatusPayload = {};

  const toNum = (v: string | null | undefined): number | undefined => {
    if (v == null || v === '') return undefined;
    const n = Number(v);
    return Number.isNaN(n) ? undefined : n;
  };

  for (const p of raw.printers ?? []) {
    result[p.deviceName] = {
      state: toNum(p.state),
      error: toNum(p.error),
      batch: p.batch ?? undefined,
    };
  }

  const cams = [
    ...(raw.aggregationCams ?? []),
    ...(raw.aggregationBoxCams ?? []),
    ...(raw.checkerCams ?? []),
  ];
  for (const c of cams) {
    result[c.deviceName] = {
      state: toNum(c.state),
      error: toNum(c.error),
      read: toNum(c.read),
      unread: toNum(c.unread),
    };
  }

  return result;
}

/**
 * Layout для экрана деталей аппарата.
 *
 * Единственный экземпляр BottomNav и Fab живёт здесь.
 * Содержимое вкладок рендерится через `<Outlet />` (вложенные маршруты).
 * WS-подключение и REST-загрузка topology также сосредоточены в этом layout,
 * а данные доступны вложенным табам через {@link DetailsProvider}.
 */
export function DetailsLayout() {
  const {
    state,
    unitsByWorkshop,
    setHeaderError,
    clearHeaderError,
    setSignalState,
    setTopologyETag,
    setDevicesTopology,
    setUnitTopology,
    setWorkshopTopology,
  } = useAppContext();
  const { workshopId = '', unitId = '' } = useParams<{
    workshopId: string;
    unitId: string;
  }>();
  const navigate = useNavigate();
  const location = useLocation();

  // ── Активная вкладка (из URL) ─────────────────────────────────────────
  const segments = location.pathname.split('/').filter(Boolean);
  const lastSegment = segments[segments.length - 1] ?? '';
  const activeTab: TabId =
    (lastSegment in ROUTE_SEGMENT_TAB
      ? ROUTE_SEGMENT_TAB[lastSegment as keyof typeof ROUTE_SEGMENT_TAB]
      : null) ?? DEFAULT_DETAIL_TAB;

  // ── WebSocket (unit-канал) ────────────────────────────────────────────
  const [lineData, setLineData] = useState<LineStatusPayload | null>(null);
  const [devicesData, setDevicesData] = useState<DevicesStatusPayload | null>(null);
  const [queueData, setQueueData] = useState<QueuePayload | null>(null);
  const [errorsData, setErrorsData] = useState<ErrorsPayload | null>(null);

  const scrollRef = useRef<HTMLElement | null>(null);

  const handleMessage = useCallback((msg: UnitWsMessage) => {
    switch (msg.type) {
      case 'LINE_STATUS':
        setLineData(msg.payload);
        break;
      case 'DEVICES_STATUS':
        setDevicesData(normalizeDevicesStatus(msg.payload));
        break;
      case 'QUEUE':
        setQueueData(msg.payload);
        break;
      case 'ERRORS':
        setErrorsData(msg.payload);
        break;
    }
  }, []);

  useUnitWs(unitId || null, handleMessage, {
    onReconnecting: () => {
      setSignalState('unit', 'reconnecting');
    },
    onError: (error) => {
      setSignalState('unit', 'error');
      setHeaderError('unit', error);
    },
    onRecovered: () => {
      setSignalState('unit', 'connected');
      clearHeaderError('unit');
    },
  });

  useEffect(() => {
    return () => {
      setSignalState('unit', 'idle');
      clearHeaderError('unit');
    };
  }, [setSignalState, clearHeaderError]);

  // При переходе между аппаратами DetailsLayout не пересоздаётся React Router'ом —
  // он остаётся примонтированным, меняется только unitId в params.
  // Явно сбрасываем все данные предыдущего аппарата, иначе вкладки (в т.ч. «Журнал»)
  // будут показывать чужие данные до прихода первого WS-сообщения нового аппарата.
  useEffect(() => {
    setLineData(null);
    setDevicesData(null);
    setQueueData(null);
    setErrorsData(null);
    setSignalState('unit', 'idle');
    clearHeaderError('unit');
  }, [unitId, setSignalState, clearHeaderError]);

  // ── Devices topology (REST) ───────────────────────────────────────────
  const hasDevicesTopology = unitId ? state.devicesTopologyByUnit[unitId] !== undefined : false;
  const devicesFetchState = useAsyncFetch<TopologyFetchResult<DevicesTopology>>(
    unitId && workshopId
      ? (signal) =>
          fetchDevicesTopology(
            workshopId,
            unitId,
            signal,
            hasDevicesTopology ? state.topologyETag : null
          )
      : null,
    [unitId, workshopId],
    { source: 'topology/devices' }
  );

  useHeaderErrorSlot('topology', devicesFetchState.error, devicesFetchState.refetch);

  useEffect(() => {
    if (!devicesFetchState.data || !unitId) return;
    const { data, etag } = devicesFetchState.data;
    if (etag) setTopologyETag(etag);
    if (data) setDevicesTopology(unitId, data);
  }, [devicesFetchState.data, unitId, setDevicesTopology, setTopologyETag]);

  const devicesTopology = unitId ? (state.devicesTopologyByUnit[unitId] ?? null) : null;
  const devicesLoading = devicesFetchState.status === 'loading' && devicesTopology === null;

  // ── Units topology (REST, фоллбэк при прямом открытии URL / reload) ──────
  // WorkshopPage грузит список аппаратов, но при прямом переходе на /units/...
  // он не посещается. Загружаем самостоятельно, только если список ещё пуст.
  const hasUnitsTopology = (state.unitTopologyByWorkshop[workshopId]?.length ?? 0) > 0;
  const unitsFetchState = useAsyncFetch<TopologyFetchResult<UnitTopology[]>>(
    unitId && workshopId && !hasUnitsTopology
      ? (signal) => fetchUnitsTopology(workshopId, signal, state.topologyETag)
      : null,
    [workshopId, hasUnitsTopology],
    { source: 'topology/units' }
  );

  useEffect(() => {
    if (!unitsFetchState.data || !workshopId) return;
    const { data, etag } = unitsFetchState.data;
    if (etag) setTopologyETag(etag);
    if (data) setUnitTopology(workshopId, data);
  }, [unitsFetchState.data, workshopId, setUnitTopology, setTopologyETag]);

  // ── Workshops topology (REST, фоллбэк при прямом открытии URL / reload) ──
  // DashboardPage грузит цеха, но при прямом переходе он не посещается.
  // Загружаем самостоятельно, только если список цехов ещё пуст.
  const hasWorkshopsTopology = state.workshopTopology.length > 0;
  const workshopsFetchState = useAsyncFetch<TopologyFetchResult<WorkshopTopology[]>>(
    workshopId && !hasWorkshopsTopology
      ? (signal) => fetchWorkshopsTopology(signal, state.topologyETag)
      : null,
    [workshopId, hasWorkshopsTopology],
    { source: 'topology/workshops' }
  );

  useEffect(() => {
    if (!workshopsFetchState.data) return;
    const { data, etag } = workshopsFetchState.data;
    if (etag) setTopologyETag(etag);
    if (data) setWorkshopTopology(data);
  }, [workshopsFetchState.data, setWorkshopTopology, setTopologyETag]);

  // ── Header ────────────────────────────────────────────────────────────
  const locationState = location.state as { workshopName?: string } | null;
  const workshopName =
    locationState?.workshopName ??
    state.workshopTopology.find((w) => w.id === workshopId)?.name ??
    DOMAIN_DEFAULTS.workshopName;

  const units = unitsByWorkshop[workshopId] ?? [];
  const currentUnit = units.find((u) => u.id === unitId);
  const unitName = currentUnit?.unit ?? DOMAIN_DEFAULTS.unitName;

  const handleBack = useCallback(() => {
    navigate(`/workshops/${workshopId}`, { state: { workshopName } });
  }, [navigate, workshopId, workshopName]);

  usePageHeader(unitName, workshopName, 'compact', handleBack);

  // ── Навигация по табам ────────────────────────────────────────────────
  const errorCount = (errorsData?.deviceErrors ?? []).filter(
    (e) => Number(e.value) !== DOMAIN_FLAGS.inactive
  ).length;

  const handleTabChange = useCallback(
    (tab: TabId) => {
      const segment = TAB_ROUTE_SEGMENT[tab];
      navigate(segment, { replace: true, state: location.state });
    },
    [navigate, location.state]
  );

  // ── Context для вложенных табов ───────────────────────────────────────
  const unitSignal = state.signalStates.unit;
  const unitError = state.headerErrors.unit?.error ?? null;
  // Агрегированная ошибка страницы: первый непустой слот из unit → topology.
  // Вкладки проверяют именно это поле, а не timing каждого канала в отдельности —
  // поэтому все секции синхронно переходят в ошибку как только ANY канал упал.
  const pageError = usePageError(['unit', 'topology']);

  const detailsValue = useMemo(
    () => ({
      lineData,
      devicesData,
      devicesTopology,
      devicesLoading,
      topologyError: devicesFetchState.error,
      queueData,
      errorsData,
      unitSignal,
      unitError,
      pageError,
    }),
    [
      lineData,
      devicesData,
      devicesTopology,
      devicesLoading,
      devicesFetchState.error,
      queueData,
      errorsData,
      unitSignal,
      unitError,
      pageError,
    ]
  );

  return (
    <div className="flex flex-col lg:flex-row" style={DETAILS_PAGE_STYLE}>
      <div className="flex flex-col flex-1 overflow-hidden min-w-0">
        <section
          ref={(el) => {
            scrollRef.current = el;
          }}
          data-scroll
          className="details-content"
          style={DETAILS_SCROLL_SECTION_STYLE}
        >
          <DetailsProvider value={detailsValue}>
            <Outlet />
          </DetailsProvider>
        </section>

        <Fab
          visible={true}
          unitId={unitId || null}
          scrollContainer={scrollRef.current}
          notification={state.notifications.get(unitId ?? '') ?? null}
        />
      </div>

      <BottomNav
        activeTab={activeTab}
        onTabChange={handleTabChange}
        errorCount={errorCount}
        className="details-nav"
      />
    </div>
  );
}
