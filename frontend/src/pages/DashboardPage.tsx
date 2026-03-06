import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchWorkshopsTopology, type TopologyFetchResult } from '../api/workshops';
import { PageHeader } from '../components/PageHeader';
import { WorkshopCard } from '../components/WorkshopCard';
import { RetryBanner } from '../components/RetryBanner';
import { WorkshopCardSkeleton } from '../components/skeleton/WorkshopCardSkeleton';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import type { WorkshopTopology } from '../types';

export function DashboardPage() {
  const { state, workshops, setWorkshopTopology, setTopologyETag } = useAppContext();
  const navigate = useNavigate();

  // Запрашиваем topology при каждом монтировании страницы (deps = []).
  // Стратегия: stale-while-revalidate на уровне приложения:
  //   • данные уже есть (hasTopology = true) → передаём ETag → на 304 стейт не трогаем,
  //     на 200 обновляем; скелетон не мигает, пользователь видит актуальный список.
  //   • данных ещё нет (первая загрузка) → ETag не передаём: 304 при отсутствии
  //     локальных данных семантически некорректен — нам нечего было бы показать.
  // cache: 'no-store' в fetchTopology исключает конкуренцию с браузерным HTTP-кешем.
  const hasTopology = state.workshopTopology.length > 0;
  const fetchState = useAsyncFetch<TopologyFetchResult<WorkshopTopology[]>>(
    (signal) => fetchWorkshopsTopology(signal, hasTopology ? state.topologyETag : null),
    [],
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
      data-scroll
      style={{
        flex: 1,
        overflowY: 'auto',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        animation: 'fadeIn 0.3s ease',
      }}
    >
      <PageHeader title="Савушкин продукт" subtitle="Площадка г. Брест" />

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
