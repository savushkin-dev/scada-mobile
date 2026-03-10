import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  DEFAULT_DETAIL_TAB,
  DETAILS_PAGE_STYLE,
  DETAILS_SCROLL_SECTION_STYLE,
  DETAIL_TABS,
  DOMAIN_DEFAULTS,
  DOMAIN_FLAGS,
  TAB_ROUTE_SEGMENT,
  ROUTE_SEGMENT_TAB,
} from '../config';
import { BottomNav } from '../components/BottomNav';
import { Fab } from '../components/Fab';
import { DetailsProvider } from '../context/DetailsContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { fetchDevicesTopology, type TopologyFetchResult } from '../api/workshops';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import { useHeaderErrorSlot } from '../hooks/useHeaderErrorSlot';
import { useUnitWs } from '../hooks/useUnitWs';
import type {
  DevicesStatusPayload,
  DevicesTopology,
  ErrorsPayload,
  LineStatusPayload,
  QueuePayload,
  TabId,
  UnitWsMessage,
} from '../types';

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
        setDevicesData(msg.payload);
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

  // ── Header ────────────────────────────────────────────────────────────
  const locationState = location.state as { workshopName?: string } | null;
  const workshopName =
    locationState?.workshopName ??
    state.workshopTopology.find((w) => w.id === workshopId)?.name ??
    DOMAIN_DEFAULTS.workshopName;

  const units = unitsByWorkshop[workshopId] ?? [];
  const currentUnit = units.find((u) => u.id === unitId);
  const unitName = currentUnit?.unit ?? unitId ?? DOMAIN_DEFAULTS.unitName;

  const handleBack = useCallback(() => {
    navigate(`/workshops/${workshopId}`, { state: { workshopName } });
  }, [navigate, workshopId, workshopName]);

  usePageHeader(unitName, workshopName, 'compact', handleBack);

  // ── Навигация по табам ────────────────────────────────────────────────
  const errorCount = (errorsData?.deviceErrors ?? []).filter(
    (e) => e.value === DOMAIN_FLAGS.active
  ).length;

  const handleTabChange = useCallback(
    (tab: TabId) => {
      const segment = TAB_ROUTE_SEGMENT[tab];
      navigate(segment, { replace: true, state: location.state });
    },
    [navigate, location.state]
  );

  // ── Context для вложенных табов ───────────────────────────────────────
  const detailsValue = useMemo(
    () => ({
      lineData,
      devicesData,
      devicesTopology,
      devicesLoading,
      queueData,
      errorsData,
    }),
    [lineData, devicesData, devicesTopology, devicesLoading, queueData, errorsData]
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
          visible={activeTab === DETAIL_TABS.batch}
          unitId={unitId || null}
          scrollContainer={scrollRef.current}
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
