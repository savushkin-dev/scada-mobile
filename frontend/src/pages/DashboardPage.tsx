import { useEffect } from 'react';
import { fetchWorkshopsTopology } from '../api/workshops';
import { WorkshopCard } from '../components/WorkshopCard';
import { RetryBanner } from '../components/RetryBanner';
import { WorkshopCardSkeleton } from '../components/skeleton/WorkshopCardSkeleton';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import type { WorkshopTopology } from '../types';

export function DashboardPage() {
  const { state, workshops, setWorkshopTopology, navigateToWorkshop } = useAppContext();

  // Загружаем topology один раз — если уже есть в памяти, пропускаем запрос.
  // Данные меняются только при изменении конфигурации (раз в несколько лет),
  // поэтому кэшируем на всё время сессии без повторных запросов.
  const topologyLoaded = state.workshopTopology.length > 0;
  const fetchState = useAsyncFetch<WorkshopTopology[]>(
    topologyLoaded ? null : (signal) => fetchWorkshopsTopology(signal),
    [topologyLoaded]
  );

  useEffect(() => {
    if (fetchState.data && !topologyLoaded) setWorkshopTopology(fetchState.data);
  }, [fetchState.data, topologyLoaded, setWorkshopTopology]);

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
      <header className="p-6 flex justify-between items-start mt-4 flex-shrink-0">
        <div>
          <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-1">
            Площадка г. Брест
          </p>
          <h1 className="text-2xl font-bold text-[#1A1C1E]">Савушкин продукт</h1>
        </div>
      </header>

      <RetryBanner error={fetchState.error} onRetry={fetchState.refetch} />

      <main className="px-4 space-y-4 pb-10">
        {fetchState.status === 'loading' && !workshops.length ? (
          <WorkshopCardSkeleton count={3} />
        ) : !workshops.length && fetchState.status !== 'loading' ? (
          <p className="text-center text-[#74777F] py-5 text-[0.88rem]">Нет данных</p>
        ) : (
          workshops.map((ws) => (
            <WorkshopCard
              key={ws.id}
              workshop={ws}
              alerts={state.alerts}
              onClick={() => navigateToWorkshop(ws.id, ws.name)}
            />
          ))
        )}
      </main>
    </section>
  );
}
