import { useCallback, useEffect } from 'react';
import { fetchUnitsTopology } from '../api/workshops';
import { UnitCard } from '../components/UnitCard';
import { RetryBanner } from '../components/RetryBanner';
import { UnitCardSkeleton } from '../components/skeleton/UnitCardSkeleton';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import { useUnitsStatusWs } from '../hooks/useUnitsStatusWs';
import type { UnitTopology, UnitsStatusMessage } from '../types';

export function WorkshopPage() {
  const { state, unitsByWorkshop, navigate, setUnitTopology, patchUnitsStatus, openDetails } =
    useAppContext();
  const workshopId = state.currentWorkshopId ?? '';
  const workshopName = state.currentWorkshopName ?? 'Цех';
  const units = unitsByWorkshop[workshopId] ?? [];

  // Загружаем topology один раз для каждого цеха.
  // При повторном заходе в тот же цех данные уже в памяти — запрос не отправляется.
  const topologyLoaded = (state.unitTopologyByWorkshop[workshopId]?.length ?? 0) > 0;
  const fetchState = useAsyncFetch<UnitTopology[]>(
    workshopId && !topologyLoaded ? (signal) => fetchUnitsTopology(workshopId, signal) : null,
    [workshopId, topologyLoaded]
  );

  useEffect(() => {
    if (fetchState.data && workshopId && !topologyLoaded) {
      setUnitTopology(workshopId, fetchState.data);
    }
  }, [fetchState.data, workshopId, topologyLoaded, setUnitTopology]);

  // Подписываемся на live-статус аппаратов этого цеха по WebSocket.
  // Хук переподключается автоматически при смене цеха.
  const handleUnitsStatus = useCallback(
    (msg: UnitsStatusMessage) => patchUnitsStatus(msg.workshopId, msg.payload),
    [patchUnitsStatus]
  );
  useUnitsStatusWs(workshopId || null, handleUnitsStatus);

  return (
    <section
      style={{
        flex: 1,
        overflowY: 'auto',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        animation: 'fadeIn 0.3s ease',
      }}
    >
      <header className="p-6 flex items-center gap-3 mt-2 flex-shrink-0">
        <button
          onClick={() => navigate('dashboard')}
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
        <div>
          <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-0.5">
            Цех
          </p>
          <h1 className="text-xl font-bold text-[#1A1C1E] leading-tight">{workshopName}</h1>
        </div>
      </header>

      <RetryBanner error={fetchState.error} onRetry={fetchState.refetch} />

      <main className="px-4 space-y-4 pb-10">
        {fetchState.status === 'loading' && !units.length ? (
          <UnitCardSkeleton count={4} />
        ) : !units.length && fetchState.status !== 'loading' ? (
          <p className="text-center text-[#74777F] py-5 text-[0.88rem]">Нет данных</p>
        ) : (
          units.map((u) => (
            <UnitCard
              key={u.id}
              unit={u}
              alerts={state.alerts}
              onClick={() => openDetails(workshopId, u.id)}
            />
          ))
        )}
      </main>
    </section>
  );
}
