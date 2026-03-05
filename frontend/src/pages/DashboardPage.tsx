import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchWorkshopsTopology, type TopologyFetchResult } from '../api/workshops';
import { WorkshopCard } from '../components/WorkshopCard';
import { RetryBanner } from '../components/RetryBanner';
import { WorkshopCardSkeleton } from '../components/skeleton/WorkshopCardSkeleton';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import type { WorkshopTopology } from '../types';

export function DashboardPage() {
  const { state, workshops, setWorkshopTopology, setTopologyETag } = useAppContext();
  const navigate = useNavigate();

  // Загружаем topology один раз — если уже есть в памяти, пропускаем запрос.
  // Данные меняются только при изменении конфигурации (раз в несколько лет),
  // поэтому кэшируем на всё время сессии без повторных запросов.
  // При наличии сохранённого ETag передаём If-None-Match — сервер вернёт
  // 304 без тела, если конфигурация не менялась.
  const topologyLoaded = state.workshopTopology.length > 0;
  const fetchState = useAsyncFetch<TopologyFetchResult<WorkshopTopology[]>>(
    topologyLoaded ? null : (signal) => fetchWorkshopsTopology(signal, state.topologyETag),
    [topologyLoaded],
    { source: 'topology/workshops' }
  );

  useEffect(() => {
    if (!fetchState.data) return;
    const { data, etag } = fetchState.data;
    // Сохраняем актуальный ETag (приходит и при 200, и при 304).
    if (etag) setTopologyETag(etag);
    // data === null при 304: конфигурация не изменилась, топология уже в стейте.
    if (data) setWorkshopTopology(data);
  }, [fetchState.data, setWorkshopTopology, setTopologyETag]);

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
      <header className="p-6 flex justify-between items-start mt-4 flex-shrink-0 sm:px-8 sm:pt-6 sm:pb-6 lg:px-10">
        <div>
          <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-1">
            Площадка г. Брест
          </p>
          <h1 className="text-2xl font-bold text-[#1A1C1E]">Савушкин продукт</h1>
        </div>
      </header>

      <RetryBanner error={fetchState.error} onRetry={fetchState.refetch} />

      <main className="px-4 space-y-4 pb-10 sm:px-6 md:grid md:grid-cols-2 md:gap-4 md:space-y-0 lg:grid-cols-3 lg:px-8">
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
              onClick={() => navigate(`/workshops/${ws.id}`, { state: { workshopName: ws.name } })}
            />
          ))
        )}
      </main>
    </section>
  );
}
