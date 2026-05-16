import { z } from 'zod';
import { DevicesTopologySchema, UnitsTopologySchema, WorkshopsTopologySchema } from '../schemas';
import type { DevicesTopology, UnitTopology, WorkshopTopology } from '../types';
import { API_BASE } from '../config';
import { apiFetch } from './client';
import { HttpError } from '../errors/AppError';

// ── Topology (статика, загружается один раз) ──────────────────────────

export interface TopologyFetchResult<T> {
  data: T | null;
  etag: string | null;
}

async function fetchTopology<T>(
  url: string,
  schema: z.ZodType<T>,
  signal?: AbortSignal,
  knownETag?: string | null
): Promise<TopologyFetchResult<T>> {
  const headers: Record<string, string> = knownETag ? { 'If-None-Match': knownETag } : {};

  const resp = await apiFetch(url, { signal, headers, cache: 'no-store' });

  if (resp.status === 304) {
    return { data: null, etag: knownETag ?? null };
  }

  if (!resp.ok) throw new HttpError(resp.status);

  const etag = resp.headers.get('ETag');
  const raw = await resp.json();
  const data = schema.parse(raw);
  return { data, etag };
}

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
