import type { Unit, Workshop } from '../types';
import { API_BASE } from '../config';
import { MOCK_UNITS, MOCK_WORKSHOPS } from '../constants/mockData';

export async function fetchWorkshops(): Promise<Workshop[]> {
  try {
    const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    return (await resp.json()) as Workshop[];
  } catch (e) {
    console.warn('[REST] /api/v1.0.0/workshops → mock:', (e as Error).message);
    return MOCK_WORKSHOPS;
  }
}

export async function fetchUnits(workshopId: string): Promise<Unit[]> {
  try {
    const resp = await fetch(`${API_BASE}/api/v1.0.0/workshops/${workshopId}/units`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    return (await resp.json()) as Unit[];
  } catch (e) {
    console.warn(`[REST] /api/v1.0.0/workshops/${workshopId}/units → mock:`, (e as Error).message);
    return MOCK_UNITS[workshopId] ?? [];
  }
}
