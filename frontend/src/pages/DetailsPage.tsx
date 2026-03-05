import { useCallback, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams, useLocation } from 'react-router-dom';
import { BatchTab } from '../components/details/BatchTab';
import { DevicesTab } from '../components/details/DevicesTab';
import { QueueTab } from '../components/details/QueueTab';
import { LogsTab } from '../components/details/LogsTab';
import { BottomNav } from '../components/BottomNav';
import { Fab } from '../components/Fab';
import { useAppContext } from '../context/AppContext';
import { useUnitWs } from '../hooks/useUnitWs';
import type {
  DevicesStatusPayload,
  ErrorsPayload,
  LineStatusPayload,
  QueuePayload,
  TabId,
  UnitWsMessage,
} from '../types';

/** Допустимые значения search-параметра «?tab=». */
const VALID_TABS = new Set<TabId>(['tab-batch', 'tab-devices', 'tab-queue', 'tab-logs']);

function parseTab(raw: string | null): TabId {
  return raw && VALID_TABS.has(raw as TabId) ? (raw as TabId) : 'tab-batch';
}

export function DetailsPage() {
  const { state, unitsByWorkshop } = useAppContext();
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

  useUnitWs(unitId || null, handleMessage);

  const errorCount = (errorsData?.deviceErrors ?? []).filter((e) => e.value === 1).length;

  // Имя цеха: приоритет — location.state (передаётся при навигации из WorkshopPage).
  // Фоллбэк — поиск по topology (актуален при прямом открытии URL / refresh).
  const locationState = location.state as { workshopName?: string } | null;
  const workshopName =
    locationState?.workshopName ??
    state.workshopTopology.find((w) => w.id === workshopId)?.name ??
    'Цех';

  // Имя аппарата из topology (может не быть загружено при прямом открытии URL).
  const units = unitsByWorkshop[workshopId] ?? [];
  const currentUnit = units.find((u) => u.id === unitId);
  const unitName = currentUnit?.unit ?? unitId ?? 'Устройство';

  function handleTabChange(tab: TabId) {
    // replace: true — не засоряем историю смену табов.
    setSearchParams({ tab }, { replace: true });
  }

  function handleBack() {
    navigate(-1);
  }

  return (
    <div
      className="flex flex-col lg:flex-row"
      style={{
        flex: 1,
        overflow: 'hidden',
        animation: 'fadeIn 0.3s ease',
      }}
    >
      {/* ── Основной контент: header + прокручиваемая область + FAB ── */}
      <div className="flex flex-col flex-1 overflow-hidden min-w-0">
        {/* Header */}
        <header
          style={{
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            flexShrink: 0,
            marginTop: '8px',
          }}
        >
          <button
            onClick={handleBack}
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '50%',
              border: 'none',
              background: '#F0F7FF',
              cursor: 'pointer',
              fontSize: '1.1rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
            aria-label="Назад"
          >
            ←
          </button>
          <div style={{ overflow: 'hidden' }}>
            <p
              style={{
                fontSize: '0.62rem',
                fontWeight: 700,
                letterSpacing: '0.08em',
                color: '#74777F',
                textTransform: 'uppercase',
                marginBottom: '2px',
              }}
            >
              {workshopName}
            </p>
            <h1
              style={{
                fontSize: '1rem',
                fontWeight: 700,
                color: '#1A1C1E',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                margin: 0,
              }}
            >
              {unitName}
            </h1>
          </div>
        </header>

        {/* Scrollable tab content */}
        <section
          ref={(el) => {
            scrollRef.current = el;
          }}
          data-scroll
          className="details-content"
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '0 16px',
            paddingBottom: '80px',
          }}
        >
          {activeTab === 'tab-batch' && <BatchTab data={lineData} />}
          {activeTab === 'tab-devices' && <DevicesTab data={devicesData} />}
          {activeTab === 'tab-queue' && <QueueTab data={queueData} />}
          {activeTab === 'tab-logs' && <LogsTab data={errorsData} />}
        </section>

        {/* FAB */}
        <Fab
          visible={activeTab === 'tab-batch'}
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
