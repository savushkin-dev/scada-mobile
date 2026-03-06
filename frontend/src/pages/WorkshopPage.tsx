import { useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { fetchUnitsTopology, type TopologyFetchResult } from '../api/workshops';
import { PageHeader } from '../components/PageHeader';
import { UnitCard } from '../components/UnitCard';
import { RetryBanner } from '../components/RetryBanner';
import { UnitCardSkeleton } from '../components/skeleton/UnitCardSkeleton';
import { useAppContext } from '../context/AppContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import type { UnitTopology } from '../types';

export function WorkshopPage() {
  const { state, unitsByWorkshop, setUnitTopology, setTopologyETag } = useAppContext();
  const { workshopId = '' } = useParams<{ workshopId: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  // Имя цеха: приоритет — location.state (передаётся при навигации из DashboardPage).
  // Фоллбэк — поиск по загруженной topology (актуален при прямом открытии URL / refresh).
  const locationState = location.state as { workshopName?: string } | null;
  const workshopName =
    locationState?.workshopName ??
    state.workshopTopology.find((w) => w.id === workshopId)?.name ??
    'Цех';

  const units = unitsByWorkshop[workshopId] ?? [];

  // Запрашиваем topology аппаратов при каждом открытии цеха и при смене цеха (deps = [workshopId]).
  // Стратегия: stale-while-revalidate на уровне приложения:
  //   • данные для этого цеха уже есть (hasUnitsTopology) → передаём ETag:
  //       304 → data === null → оставляем текущий стейт, скелетон не показываем
  //       200 → data !== null → обновляем (переименование аппарата и т.п.)
  //   • данных нет (первый визит в цех) → ETag не передаём:
  //       state.topologyETag мог быть установлен после загрузки topology цехов,
  //       но у нас нет локальных данных — 304 оставил бы нас с пустым списком.
  // cache: 'no-store' в fetchTopology исключает конкуренцию с браузерным HTTP-кешем.
  const hasUnitsTopology = (state.unitTopologyByWorkshop[workshopId]?.length ?? 0) > 0;
  const fetchState = useAsyncFetch<TopologyFetchResult<UnitTopology[]>>(
    workshopId
      ? (signal) =>
          fetchUnitsTopology(workshopId, signal, hasUnitsTopology ? state.topologyETag : null)
      : null,
    [workshopId],
    { source: 'topology/units' }
  );

  useEffect(() => {
    if (!fetchState.data || !workshopId) return;
    const { data, etag } = fetchState.data;
    // Сохраняем актуальный ETag (приходит и при 200, и при 304).
    if (etag) setTopologyETag(etag);
    // data === null при 304: конфигурация не изменилась, топология уже в стейте.
    if (data) setUnitTopology(workshopId, data);
  }, [fetchState.data, workshopId, setUnitTopology, setTopologyETag]);

  // live-статус аппаратов подписывается через /ws/live в RootLayout (useLiveWs).
  // SUBSCRIBE_WORKSHOP отправляется автоматически при открытии этого маршрута.

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
      <PageHeader title={workshopName} subtitle="Цех" onBack={() => navigate(-1)} />

      <RetryBanner error={fetchState.error} onRetry={fetchState.refetch} />

      <main className="px-4 space-y-4 pb-10 sm:px-6 md:grid md:grid-cols-2 md:gap-4 md:space-y-0 lg:grid-cols-3 lg:px-8">
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
              onClick={() =>
                navigate(`/workshops/${workshopId}/units/${u.id}`, {
                  state: { workshopName },
                })
              }
            />
          ))
        )}
      </main>
    </section>
  );
}
