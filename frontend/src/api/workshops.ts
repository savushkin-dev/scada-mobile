import { z } from 'zod';
import { DevicesTopologySchema, UnitsTopologySchema, WorkshopsTopologySchema } from '../schemas';
import type { DevicesTopology, UnitTopology, WorkshopTopology } from '../types';
import { API_BASE } from '../config';
import { getAuthUserId } from '../auth/session';
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
 *
 * @param schema Zod-схема для валидации тела ответа.
 *               ZodError при несоответствии пробрасывается выше и
 *               классифицируется как `validation_error` в classifyError.
 */
async function fetchTopology<T>(
  url: string,
  schema: z.ZodType<T>,
  signal?: AbortSignal,
  knownETag?: string | null
): Promise<TopologyFetchResult<T>> {
  const headers: Record<string, string> = knownETag ? { 'If-None-Match': knownETag } : {};
  const userId = getAuthUserId();
  if (userId) headers['X-User-Id'] = userId;
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
  const raw = await resp.json();
  // schema.parse() бросает ZodError при несоответствии контракту.
  // classifyError обработает его как `validation_error`.
  const data = schema.parse(raw);
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
  return fetchTopology(
    `${API_BASE}/api/v1.0.0/workshops/topology`,
    WorkshopsTopologySchema,
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
  return fetchTopology(
    `${API_BASE}/api/v1.0.0/workshops/${workshopId}/units/topology`,
    UnitsTopologySchema,
    signal,
    knownETag
  );
}

/**
 * Загружает статическую топологию устройств аппарата (принтеры, камеры).
 * Тот же ETag, что и у остальных topology-эндпоинтов (единый хэш конфигурации) —
 * передайте `knownETag` для получения 304 при неизменной конфигурации.
 *
 * Ответ 404 если цех не найден, или если unitId не принадлежит указанному workshopId.
 */
export function fetchDevicesTopology(
  workshopId: string,
  unitId: string,
  signal?: AbortSignal,
  knownETag?: string | null
): Promise<TopologyFetchResult<DevicesTopology>> {
  return fetchTopology(
    `${API_BASE}/api/v1.0.0/workshops/${workshopId}/units/${unitId}/devices/topology`,
    DevicesTopologySchema,
    signal,
    knownETag
  );
}
