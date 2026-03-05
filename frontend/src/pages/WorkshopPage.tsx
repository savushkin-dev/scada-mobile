import { useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { fetchUnitsTopology, type TopologyFetchResult } from '../api/workshops';
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

  // Загружаем topology один раз для каждого цеха.
  // При повторном заходе в тот же цех данные уже в памяти — запрос не отправляется.
  //
  // Важно: If-None-Match НЕ передаётся здесь намеренно.
  // Этот fetch выполняется только когда topologyLoaded = false — данных у нас нет.
  // Отправить If-None-Match без кэшированных данных семантически некорректно: сервер
  // вернёт 304, мы получим data = null и топология аппаратов так и не загрузится.
  // (state.topologyETag мог быть уже установлен после загрузки топологии цехов,
  //  поскольку оба эндпоинта используют один ETag хэша конфигурации.)
  const topologyLoaded = (state.unitTopologyByWorkshop[workshopId]?.length ?? 0) > 0;
  const fetchState = useAsyncFetch<TopologyFetchResult<UnitTopology[]>>(
    workshopId && !topologyLoaded ? (signal) => fetchUnitsTopology(workshopId, signal) : null,
    [workshopId, topologyLoaded],
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
      style={{
        flex: 1,
        overflowY: 'auto',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        animation: 'fadeIn 0.3s ease',
      }}
    >
      <header className="p-6 flex items-center gap-3 mt-2 flex-shrink-0 sm:px-8 lg:px-10">
        <button
          onClick={() => navigate(-1)}
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
