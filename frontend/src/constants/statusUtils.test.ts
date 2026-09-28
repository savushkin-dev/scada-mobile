import { describe, expect, it } from 'vitest';
import { getDeviceStatusLevel } from './statusUtils';
import type { DevicesStatusPayload } from '../types';

describe('getDeviceStatusLevel', () => {
  it('pending — WS-данные ещё не пришли', () => {
    expect(getDeviceStatusLevel(null, 'Printer11')).toBe('pending');
  });

  it('pending — устройства нет в payload', () => {
    expect(getDeviceStatusLevel({}, 'Printer11')).toBe('pending');
  });

  it('disconnected — устройство отключено (даже при Error=1)', () => {
    const wsData: DevicesStatusPayload = {
      Printer11: { st: 0, error: 1, disconnected: true },
    };
    expect(getDeviceStatusLevel(wsData, 'Printer11')).toBe('disconnected');
  });

  it('error — реальная ошибка устройства (Error=1)', () => {
    const wsData: DevicesStatusPayload = {
      Printer11: { st: 1, error: 1, disconnected: false },
    };
    expect(getDeviceStatusLevel(wsData, 'Printer11')).toBe('error');
  });

  // Регрессия issue #79: работающее устройство (ST=1, Error=0) — зелёное, не красное.
  it('ok — устройство работает (ST=1) без ошибки (Error=0)', () => {
    const wsData: DevicesStatusPayload = {
      Printer11: { st: 1, error: 0, disconnected: false },
    };
    expect(getDeviceStatusLevel(wsData, 'Printer11')).toBe('ok');
  });

  it('ok — устройство остановлено (ST=0) без ошибки', () => {
    const wsData: DevicesStatusPayload = {
      Printer11: { st: 0, error: 0, disconnected: false },
    };
    expect(getDeviceStatusLevel(wsData, 'Printer11')).toBe('ok');
  });
});
