import type { Unit, UnitTopology, Workshop, WorkshopTopology } from '../types';
import { API_BASE } from '../config';
import { HttpError } from '../errors/AppError';

// ── Topology (статика, загружается один раз) ──────────────────────────

/**
 * Результат загрузки topology-ресурса.
 *
 * - `data` — тело ответа. `null` означает HTTP 304 Not Modified:
 *   сервер подтвердил, что данные не изменились; клиент должен
 *   использовать уже имеющуюся копию.
 * - `etag` — значение заголовка `ETag` из ответа (с кавычками, как отдал сервер),
 *   для передачи в `If-None-Match` при следующем запросе.
 *   При 304 возвращается переданный `knownETag` (он не изменился).
 */
export interface TopologyFetchResult<T> {
  data: T | null;
  etag: string | null;
}

/**
 * Shared helper: выполняет GET с опциональным If-None-Match,
 * обрабатывает 304 и извлекает ETag из ответа.
 */
async function fetchTopology<T>(
  url: string,
  signal?: AbortSignal,
  knownETag?: string | null
): Promise<TopologyFetchResult<T>> {
  const headers: HeadersInit = knownETag ? { 'If-None-Match': knownETag } : {};
  // cache: 'no-store' — браузерный HTTP-кеш полностью обходится.
  // ETag-логикой управляет приложение само через If-None-Match / 304.
  // Без этого браузер может отдать max-age-ответ из HTTP-кеша прежде,
  // чем наш код отправит If-None-Match, нарушив семантику условного GET.
  const resp = await fetch(url, { signal, headers, cache: 'no-store' });

  if (resp.status === 304) {
    // Данные не изменились: возвращаем null, чтобы вызывающий код
    // сохранил текущее состояние без перезаписи.
    return { data: null, etag: knownETag ?? null };
  }

  // Бросаем HttpError (не generic Error) — несёт числовой status,
  // что позволяет classifyError точно определить тип без парсинга строк.
  if (!resp.ok) throw new HttpError(resp.status);

  const etag = resp.headers.get('ETag');
  const data = (await resp.json()) as T;
  return { data, etag };
}

/**
 * Загружает статическую топологию цехов.
 * Передайте `knownETag`, если он был получен ранее — сервер вернёт
 * 304 вместо тела при неизменной конфигурации.
 */
export function fetchWorkshopsTopology(
  signal?: AbortSignal,
  knownETag?: string | null
): Promise<TopologyFetchResult<WorkshopTopology[]>> {
  return fetchTopology<WorkshopTopology[]>(
    `${API_BASE}/api/v1.0.0/workshops/topology`,
    signal,
    knownETag
  );
}

/**
 * Загружает статическую топологию аппаратов цеха.
 * Передайте `knownETag`, если он был получен ранее — сервер вернёт
 * 304 вместо тела при неизменной конфигурации.
 */
export function fetchUnitsTopology(
  workshopId: string,
  signal?: AbortSignal,
  knownETag?: string | null
): Promise<TopologyFetchResult<UnitTopology[]>> {
  return fetchTopology<UnitTopology[]>(
    `${API_BASE}/api/v1.0.0/workshops/${workshopId}/units/topology`,
    signal,
    knownETag
  );
}

// ── Legacy (deprecated, только для обратной совместимости) ────────────

/**
 * @deprecated Используй {@link fetchWorkshopsTopology} + WS /ws/workshops/status.
 */
export async function fetchWorkshops(signal?: AbortSignal): Promise<Workshop[]> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops`, { signal });
  if (!resp.ok) throw new HttpError(resp.status);
  return resp.json() as Promise<Workshop[]>;
}

/**
 * @deprecated Используй {@link fetchUnitsTopology} + WS /ws/workshops/{id}/units/status.
 */
export async function fetchUnits(workshopId: string, signal?: AbortSignal): Promise<Unit[]> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops/${workshopId}/units`, { signal });
  if (!resp.ok) throw new HttpError(resp.status);
  return resp.json() as Promise<Unit[]>;
}
