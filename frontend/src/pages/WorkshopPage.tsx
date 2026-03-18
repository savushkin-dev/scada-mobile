import { useCallback, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { DOMAIN_DEFAULTS, PAGE_FADE_SECTION_STYLE, UI_BEHAVIOR, UI_COPY } from '../config';
import { fetchUnitsTopology, type TopologyFetchResult } from '../api/workshops';
import { UnitCard } from '../components/UnitCard';
import { UnitCardSkeleton } from '../components/skeleton/UnitCardSkeleton';
import { useAppContext } from '../context/AppContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import { useHeaderErrorSlot } from '../hooks/useHeaderErrorSlot';
import { usePageError } from '../hooks/usePageError';
import { getErrorBodyMessage } from '../errors/AppError';
import { getUnitStatusLevel } from '../constants/statusUtils';
import { DETAIL_TABS, TAB_ROUTE_SEGMENT } from '../config/ui';
import type { UnitTopology } from '../types';

/**
 * Экран цеха: список аппаратов и переход в детали аппарата.
 *
 * Роль страницы:
 * - загружает topology аппаратов выбранного цеха;
 * - отображает live-статусы аппаратов из AppContext;
 * - выбирает стартовую вкладку деталей по статусу аппарата
 *   (critical -> logs, иначе batch).
 *
 * Критерии статуса аппарата определены в {@link ../constants/statusUtils.ts}.
 */

export function WorkshopPage() {
  const { state, unitsByWorkshop, setUnitTopology, setTopologyETag } = useAppContext();
  const { workshopId = '' } = useParams<{ workshopId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const liveSignal = state.signalStates.live;

  // Имя цеха: приоритет — location.state (передаётся при навигации из DashboardPage).
  // Фоллбэк — поиск по загруженной topology (актуален при прямом открытии URL / refresh).
  const locationState = location.state as { workshopName?: string } | null;
  const workshopName =
    locationState?.workshopName ??
    state.workshopTopology.find((w) => w.id === workshopId)?.name ??
    DOMAIN_DEFAULTS.workshopName;

  // Явная иерархическая навигация к корневой странице — независимо от
  // состояния браузерной истории (прямые ссылки, обновление страницы).
  const handleBack = useCallback(() => navigate('/'), [navigate]);

  // Шапка: имя цеха + кнопка «назад».
  usePageHeader(workshopName, UI_COPY.workshopSubtitle, undefined, handleBack);

  const units = unitsByWorkshop[workshopId] ?? [];

  // Запрашиваем topology аппаратов при каждом открытии цеха и при смене цеха (deps = [workshopId]).
  const hasUnitsTopology =
    (state.unitTopologyByWorkshop[workshopId]?.length ?? UI_BEHAVIOR.emptyCollectionSize) >
    UI_BEHAVIOR.emptyCollectionSize;
  const fetchState = useAsyncFetch<TopologyFetchResult<UnitTopology[]>>(
    workshopId
      ? (signal) =>
          fetchUnitsTopology(workshopId, signal, hasUnitsTopology ? state.topologyETag : null)
      : null,
    [workshopId],
    { source: 'topology/units' }
  );

  useHeaderErrorSlot('topology', fetchState.error, fetchState.refetch);

  const pageError = usePageError(['live', 'topology']);
  const isErrorState = pageError !== null && (!units.length || liveSignal === 'error');

  useEffect(() => {
    if (!fetchState.data || !workshopId) return;
    const { data, etag } = fetchState.data;
    if (etag) setTopologyETag(etag);
    if (data) setUnitTopology(workshopId, data);
  }, [fetchState.data, workshopId, setUnitTopology, setTopologyETag]);

  return (
    <section data-scroll style={PAGE_FADE_SECTION_STYLE}>
      <main className="px-4 space-y-4 pb-10 sm:px-6 md:grid md:grid-cols-2 md:gap-4 md:space-y-0 lg:grid-cols-3 lg:px-8">
        {isErrorState ? (
          <p className="text-center text-[#74777F] py-10 text-[0.88rem] col-span-full">
            {getErrorBodyMessage(pageError)}
          </p>
        ) : liveSignal === 'reconnecting' ? (
          <UnitCardSkeleton count={Math.max(units.length, UI_BEHAVIOR.workshopSkeletonCount)} />
        ) : fetchState.status === 'loading' && !units.length ? (
          <UnitCardSkeleton count={UI_BEHAVIOR.workshopSkeletonCount} />
        ) : !units.length && fetchState.status === 'success' ? (
          <p className="text-center text-[#74777F] py-5 text-[0.88rem]">{UI_COPY.noData}</p>
        ) : !units.length ? null : (
          units.map((u) => (
            <UnitCard
              key={u.id}
              unit={u}
              alerts={state.alerts}
              onClick={() => {
                const targetTab =
                  getUnitStatusLevel(u, state.alerts) === 'critical'
                    ? DETAIL_TABS.logs
                    : DETAIL_TABS.batch;

                navigate(`/workshops/${workshopId}/units/${u.id}/${TAB_ROUTE_SEGMENT[targetTab]}`, {
                  state: { workshopName },
                });
              }}
            />
          ))
        )}
      </main>
    </section>
  );
}
