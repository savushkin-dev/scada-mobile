import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams, useLocation } from 'react-router-dom';
import {
  DEFAULT_DETAIL_TAB,
  DETAILS_PAGE_STYLE,
  DETAILS_SCROLL_SECTION_STYLE,
  DETAIL_TABS,
  DOMAIN_DEFAULTS,
  DOMAIN_FLAGS,
  VALID_DETAIL_TABS,
} from '../config';
import { BatchTab } from '../components/details/BatchTab';
import { DevicesTab } from '../components/details/DevicesTab';
import { QueueTab } from '../components/details/QueueTab';
import { LogsTab } from '../components/details/LogsTab';
import { BottomNav } from '../components/BottomNav';
import { Fab } from '../components/Fab';
import { PageHeader } from '../components/PageHeader';
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

function parseTab(raw: string | null): TabId {
  return raw && VALID_DETAIL_TABS.has(raw as TabId) ? (raw as TabId) : DEFAULT_DETAIL_TAB;
}

export function DetailsPage() {
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
  const [searchParams, setSearchParams] = useSearchParams();

  // Активный таб из URL (?tab=tab-batch). Неизвестные значения → tab-batch.
  const activeTab = parseTab(searchParams.get('tab'));

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

  // ── Devices topology (REST) ──────────────────────────────────────────────
  // Стратегия аналогична Workshop/Dashboard: stale-while-revalidate с ETag.
  // deps [unitId, workshopId] — перезапрашиваем при смене аппарата.
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

  const errorCount = (errorsData?.deviceErrors ?? []).filter(
    (e) => e.value === DOMAIN_FLAGS.active
  ).length;

  // Имя цеха: приоритет — location.state (передаётся при навигации из WorkshopPage).
  // Фоллбэк — поиск по topology (актуален при прямом открытии URL / refresh).
  const locationState = location.state as { workshopName?: string } | null;
  const workshopName =
    locationState?.workshopName ??
    state.workshopTopology.find((w) => w.id === workshopId)?.name ??
    DOMAIN_DEFAULTS.workshopName;

  // Имя аппарата из topology (может не быть загружено при прямом открытии URL).
  const units = unitsByWorkshop[workshopId] ?? [];
  const currentUnit = units.find((u) => u.id === unitId);
  const unitName = currentUnit?.unit ?? unitId ?? DOMAIN_DEFAULTS.unitName;

  function handleTabChange(tab: TabId) {
    // replace: true — не засоряем историю смену табов.
    setSearchParams({ tab }, { replace: true });
  }

  function handleBack() {
    navigate(-1);
  }

  return (
    <div className="flex flex-col lg:flex-row" style={DETAILS_PAGE_STYLE}>
      {/* ── Основной контент: header + прокручиваемая область + FAB ── */}
      <div className="flex flex-col flex-1 overflow-hidden min-w-0">
        <PageHeader
          title={unitName}
          subtitle={workshopName}
          variant="compact"
          sticky={false}
          onBack={handleBack}
        />

        {/* Scrollable tab content */}
        <section
          ref={(el) => {
            scrollRef.current = el;
          }}
          data-scroll
          className="details-content"
          style={DETAILS_SCROLL_SECTION_STYLE}
        >
          {activeTab === DETAIL_TABS.batch && <BatchTab data={lineData} />}
          {activeTab === DETAIL_TABS.devices && (
            <DevicesTab topology={devicesTopology} isLoading={devicesLoading} data={devicesData} />
          )}
          {activeTab === DETAIL_TABS.queue && <QueueTab data={queueData} />}
          {activeTab === DETAIL_TABS.logs && <LogsTab data={errorsData} />}
        </section>

        {/* FAB */}
        <Fab
          visible={activeTab === DETAIL_TABS.batch}
          unitId={unitId || null}
          scrollContainer={scrollRef.current}
        />
      </div>
      {/* /details-main */}

      {/* Nav: bottom на мобильном, боковая панель на десктопе */}
      <BottomNav
        activeTab={activeTab}
        onTabChange={handleTabChange}
        errorCount={errorCount}
        className="details-nav"
      />
    </div>
  );
}
