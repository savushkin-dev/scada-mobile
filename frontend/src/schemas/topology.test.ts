import { describe, expect, it } from 'vitest';
import {
  DeviceGroupSchema,
  DeviceMetaSchema,
  DevicesTopologySchema,
  UnitTopologySchema,
  WorkshopTopologySchema,
} from './topology';

describe('WorkshopTopologySchema', () => {
  it('валидный цех парсится', () => {
    expect(WorkshopTopologySchema.parse({ id: 3, name: 'Цех упаковки', totalUnits: 12 })).toEqual({
      id: 3,
      name: 'Цех упаковки',
      totalUnits: 12,
    });
  });

  it('id: 0 — отказ (positive)', () => {
    expect(WorkshopTopologySchema.safeParse({ id: 0, name: 'x', totalUnits: 1 }).success).toBe(
      false
    );
  });

  it('отрицательный totalUnits — отказ (nonnegative)', () => {
    expect(WorkshopTopologySchema.safeParse({ id: 1, name: 'x', totalUnits: -1 }).success).toBe(
      false
    );
  });

  it('отсутствует name — отказ', () => {
    expect(WorkshopTopologySchema.safeParse({ id: 1, totalUnits: 0 }).success).toBe(false);
  });
});

describe('UnitTopologySchema', () => {
  it('валидный аппарат парсится', () => {
    expect(UnitTopologySchema.parse({ id: 'Line', workshopId: 5, unit: 'Линия 1' })).toEqual({
      id: 'Line',
      workshopId: 5,
      unit: 'Линия 1',
    });
  });

  it('id как число — отказ', () => {
    expect(UnitTopologySchema.safeParse({ id: 11, workshopId: 5, unit: 'x' }).success).toBe(false);
  });
});

describe('DeviceGroupSchema', () => {
  it('валидная группа парсится', () => {
    expect(
      DeviceGroupSchema.parse({ label: 'Поток 1', order: 1, codes: ['Printer11', 'CamAgregation'] })
    ).toEqual({ label: 'Поток 1', order: 1, codes: ['Printer11', 'CamAgregation'] });
  });

  it('order как строка — отказ', () => {
    expect(DeviceGroupSchema.safeParse({ label: 'g', order: '1', codes: [] }).success).toBe(false);
  });
});

describe('DeviceMetaSchema', () => {
  it('без currentBatch парсится', () => {
    expect(DeviceMetaSchema.parse({ displayName: 'Принтер', showCounters: true })).toEqual({
      displayName: 'Принтер',
      showCounters: true,
    });
  });

  it('currentBatch: null парсится', () => {
    expect(
      DeviceMetaSchema.parse({ displayName: 'Принтер', showCounters: false, currentBatch: null })
        .currentBatch
    ).toBeNull();
  });

  it('currentBatch со значением партии сохраняется', () => {
    expect(
      DeviceMetaSchema.parse({
        displayName: 'Принтер',
        showCounters: true,
        currentBatch: '1605 | 328 | 25.09.2026',
      }).currentBatch
    ).toBe('1605 | 328 | 25.09.2026');
  });
});

describe('DevicesTopologySchema', () => {
  const base = {
    unitId: 'Line',
    workshopId: 5,
    unit: 'Линия 1',
    devices: {
      printers: ['Printer11'],
      aggregationCams: ['CamAgregation'],
      aggregationBoxCams: [],
      checkerCams: [],
    },
    deviceNames: { Printer11: 'Принтер 11' },
    typeNames: { MARK: 'Принтер' },
  };

  it('без groups/deviceMeta парсится с дефолтами', () => {
    const parsed = DevicesTopologySchema.parse(base);
    expect(parsed.groups).toEqual([]);
    expect(parsed.deviceMeta).toEqual({});
  });

  it('с groups и deviceMeta парсится', () => {
    const parsed = DevicesTopologySchema.parse({
      ...base,
      groups: [{ label: 'Поток 1', order: 1, codes: ['Printer11'] }],
      deviceMeta: {
        Printer11: { displayName: 'Принтер 11', showCounters: true, currentBatch: null },
      },
    });
    expect(parsed.groups).toHaveLength(1);
    expect(parsed.deviceMeta.Printer11.showCounters).toBe(true);
  });

  it('отсутствует printers в devices — отказ', () => {
    const broken = {
      ...base,
      devices: {
        aggregationCams: [],
        aggregationBoxCams: [],
        checkerCams: [],
      },
    };
    expect(DevicesTopologySchema.safeParse(broken).success).toBe(false);
  });

  it('workshopId дробный — отказ', () => {
    expect(DevicesTopologySchema.safeParse({ ...base, workshopId: 1.5 }).success).toBe(false);
  });
});
