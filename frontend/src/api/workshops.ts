import type { Unit, UnitTopology, Workshop, WorkshopTopology } from '../types';
import { API_BASE } from '../config';

// ── Topology (статика, загружается один раз) ──────────────────────────

/**
 * Загружает статическую топологию цехов.
 * Ответ содержит заголовок ETag — следующим шагом будет поддержка If-None-Match.
 * Бросает Error при сетевой ошибке или не-2xx ответе (для retry-хука).
 */
export async function fetchWorkshopsTopology(signal?: AbortSignal): Promise<WorkshopTopology[]> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops/topology`, { signal });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json() as Promise<WorkshopTopology[]>;
}

/**
 * Загружает статическую топологию аппаратов цеха.
 * Ответ содержит заголовок ETag — следующим шагом будет поддержка If-None-Match.
 * Бросает Error при сетевой ошибке или не-2xx ответе (для retry-хука).
 */
export async function fetchUnitsTopology(
  workshopId: string,
  signal?: AbortSignal
): Promise<UnitTopology[]> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops/${workshopId}/units/topology`, {
    signal,
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json() as Promise<UnitTopology[]>;
}

// ── Legacy (deprecated, только для обратной совместимости) ────────────

/**
 * @deprecated Используй {@link fetchWorkshopsTopology} + WS /ws/workshops/status.
 */
export async function fetchWorkshops(signal?: AbortSignal): Promise<Workshop[]> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops`, { signal });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json() as Promise<Workshop[]>;
}

/**
 * @deprecated Используй {@link fetchUnitsTopology} + WS /ws/workshops/{id}/units/status.
 */
export async function fetchUnits(workshopId: string, signal?: AbortSignal): Promise<Unit[]> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops/${workshopId}/units`, { signal });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json() as Promise<Unit[]>;
}
