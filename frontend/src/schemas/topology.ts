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
  id: z.number().int().positive(),
  name: z.string(),
  totalUnits: z.number().int().nonnegative(),
});

/** Обёртка-массив для ответа GET /workshops/topology */
export const WorkshopsTopologySchema = z.array(WorkshopTopologySchema);

// ── GET /workshops/{id}/units/topology ────────────────────────────────

export const UnitTopologySchema = z.object({
  id: z.string(),
  workshopId: z.number().int().positive(),
  unit: z.string(),
});

/** Обёртка-массив для ответа GET /workshops/{id}/units/topology */
export const UnitsTopologySchema = z.array(UnitTopologySchema);

// ── GET /workshops/{id}/units/{unitId}/devices/topology ───────────────

/** Группа устройств в раскладке автомата (per-unit конфиг на backend). */
export const DeviceGroupSchema = z.object({
  /** Заголовок группы: имя автомата, «Поток», «Поток 2», «Агрегация» и т.п. */
  label: z.string(),
  /** Порядок группы при отрисовке (первая группа — машинная). */
  order: z.number().int(),
  /** Коды устройств внутри группы, в порядке отображения. */
  codes: z.array(z.string()),
});

/** Per-unit мета-информация об устройстве (имя на экране + признак счётчиков). */
export const DeviceMetaSchema = z.object({
  /** Отображаемое имя с учётом per-unit переопределения. */
  displayName: z.string(),
  /** Показывать ли блок «Считано/Несчитано» на карточке. */
  showCounters: z.boolean(),
  /** Текущая партия устройства ("1605 | 328 | 25.09.2026"), если известна. */
  currentBatch: z.string().nullable().optional(),
});

export const DevicesTopologySchema = z.object({
  unitId: z.string(),
  workshopId: z.number().int().positive(),
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
  /** Отображаемые имена устройств из справочника: код устройства → device_catalog.name */
  deviceNames: z.record(z.string(), z.string()),
  /** Отображаемые имена типов устройств: код типа → device_types.name */
  typeNames: z.record(z.string(), z.string()),
  /**
   * Раскладка устройств по группам (per-unit конфиг на backend).
   * Опционально: при отсутствии вкладка «Устройства» рисует legacy-группы по типам.
   */
  groups: z.array(DeviceGroupSchema).optional().default([]),
  /** Per-unit мета устройств: код → { displayName, showCounters }. */
  deviceMeta: z.record(z.string(), DeviceMetaSchema).optional().default({}),
});

// ── Выводимые типы ────────────────────────────────────────────────────

export type WorkshopTopology = z.infer<typeof WorkshopTopologySchema>;
export type UnitTopology = z.infer<typeof UnitTopologySchema>;
export type DeviceGroup = z.infer<typeof DeviceGroupSchema>;
export type DeviceMeta = z.infer<typeof DeviceMetaSchema>;
export type DevicesTopology = z.infer<typeof DevicesTopologySchema>;
