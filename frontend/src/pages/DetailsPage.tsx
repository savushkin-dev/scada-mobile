import { useCallback, useRef, useState } from 'react';
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

export function DetailsPage() {
  const { state, navigate, activateTab } = useAppContext();
  const { currentUnitId, currentWorkshopId, currentWorkshopName, activeTab } = state;

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

  useUnitWs(currentUnitId, handleMessage);

  const errorCount = (errorsData?.deviceErrors ?? []).filter((e) => e.value === 1).length;

  // Find unit name from state
  const units = currentWorkshopId ? (state.unitsByWorkshop[currentWorkshopId] ?? []) : [];
  const currentUnit = units.find((u) => u.id === currentUnitId);
  const unitName = currentUnit?.unit ?? currentUnitId ?? 'Устройство';

  function handleTabChange(tab: TabId) {
    activateTab(tab);
  }

  function handleBack() {
    navigate('workshop');
  }

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        animation: 'fadeIn 0.3s ease',
      }}
    >
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
            {currentWorkshopName ?? 'Цех'}
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
        unitId={currentUnitId}
        scrollContainer={scrollRef.current}
      />

      {/* Bottom Nav */}
      <BottomNav activeTab={activeTab} onTabChange={handleTabChange} errorCount={errorCount} />
    </div>
  );
}
