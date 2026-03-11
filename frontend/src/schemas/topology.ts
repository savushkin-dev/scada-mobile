/**
 * Zod-схемы для статической топологии — данных, получаемых по REST API.
 *
 * Эти схемы — единственный источник правды для типов топологии.
 * TS-типы (WorkshopTopology, UnitTopology, DevicesTopology) выводятся
 * через z.infer<> в types/index.ts — дубликация исключена.
 */
import { z } from 'zod';

// ── GET /workshops/topology ───────────────────────────────────────────

export const WorkshopTopologySchema = z.object({
  id: z.string(),
  name: z.string(),
  totalUnits: z.number().int().nonnegative(),
});

/** Обёртка-массив для ответа GET /workshops/topology */
export const WorkshopsTopologySchema = z.array(WorkshopTopologySchema);

// ── GET /workshops/{id}/units/topology ────────────────────────────────

export const UnitTopologySchema = z.object({
  id: z.string(),
  workshopId: z.string(),
  unit: z.string(),
});

/** Обёртка-массив для ответа GET /workshops/{id}/units/topology */
export const UnitsTopologySchema = z.array(UnitTopologySchema);

// ── GET /workshops/{id}/units/{unitId}/devices/topology ───────────────

export const DevicesTopologySchema = z.object({
  unitId: z.string(),
  workshopId: z.string(),
  unit: z.string(),
  devices: z.object({
    /** Принтеры маркировки */
    printers: z.array(z.string()),
    /** Камеры агрегации на потоке */
    aggregationCams: z.array(z.string()),
    /** Камеры агрегации на коробе */
    aggregationBoxCams: z.array(z.string()),
    /** Камеры проверки */
    checkerCams: z.array(z.string()),
  }),
});

// ── Выводимые типы ────────────────────────────────────────────────────

export type WorkshopTopology = z.infer<typeof WorkshopTopologySchema>;
export type UnitTopology = z.infer<typeof UnitTopologySchema>;
export type DevicesTopology = z.infer<typeof DevicesTopologySchema>;
