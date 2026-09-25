import { describe, expect, it } from 'vitest';
import {
  AssignedUnitSchema,
  NotificationSettingSchema,
  NotificationSettingsSchema,
  UserProfileSchema,
} from './profile';

describe('AssignedUnitSchema', () => {
  it('валидная назначенная единица парсится', () => {
    expect(AssignedUnitSchema.parse({ unitId: 'Line', unitName: 'Линия 1' })).toEqual({
      unitId: 'Line',
      unitName: 'Линия 1',
    });
  });

  it('printsrvInstanceId: null и отсутствие — оба допустимы', () => {
    expect(
      AssignedUnitSchema.parse({ unitId: 'u', unitName: 'n', printsrvInstanceId: null })
        .printsrvInstanceId
    ).toBeNull();
    expect(
      AssignedUnitSchema.parse({ unitId: 'u', unitName: 'n' }).printsrvInstanceId
    ).toBeUndefined();
  });

  it('отсутствует unitName — отказ', () => {
    expect(AssignedUnitSchema.safeParse({ unitId: 'u' }).success).toBe(false);
  });
});

describe('UserProfileSchema', () => {
  const valid = {
    fullName: 'Иван Петров',
    role: 'TECH',
    workerCode: '1024',
    assignedUnits: [{ unitId: 'Line', unitName: 'Линия 1' }],
  };

  it('валидный профиль парсится', () => {
    expect(UserProfileSchema.parse(valid).workerCode).toBe('1024');
  });

  it('пустой assignedUnits допустим', () => {
    expect(UserProfileSchema.parse({ ...valid, assignedUnits: [] }).assignedUnits).toHaveLength(0);
  });

  it('невалидный элемент в assignedUnits — отказ', () => {
    expect(
      UserProfileSchema.safeParse({ ...valid, assignedUnits: [{ unitId: 'u' }] }).success
    ).toBe(false);
  });

  it('отсутствует workerCode — отказ', () => {
    const { workerCode: _omit, ...broken } = valid;
    expect(UserProfileSchema.safeParse(broken).success).toBe(false);
  });
});

describe('NotificationSettingSchema / NotificationSettingsSchema', () => {
  const setting = {
    unitId: 'Line',
    unitName: 'Линия 1',
    techEnabled: true,
    masterEnabled: false,
  };

  it('валидная настройка парсится', () => {
    expect(NotificationSettingSchema.parse(setting).techEnabled).toBe(true);
  });

  it('массив настроек парсится', () => {
    expect(NotificationSettingsSchema.parse([setting, setting])).toHaveLength(2);
  });

  it('techEnabled как строка — отказ', () => {
    expect(NotificationSettingSchema.safeParse({ ...setting, techEnabled: 'yes' }).success).toBe(
      false
    );
  });

  it('не массив — отказ', () => {
    expect(NotificationSettingsSchema.safeParse(setting).success).toBe(false);
  });
});
