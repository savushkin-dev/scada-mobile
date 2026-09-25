import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getTokenExpiryDate,
  getTokenTimeRemaining,
  isTemporaryPasswordToken,
  isTokenExpired,
  isTokenFullyExpired,
} from './token';

/** Собирает JWT вида header.payload.signature из plain payload. */
function makeJwt(payload: Record<string, unknown>): string {
  const b64url = (obj: unknown) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  return `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url(payload)}.testsig`;
}

const NOW_SEC = 1_800_000_000;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(NOW_SEC * 1000));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('isTokenExpired (margin 60с)', () => {
  it('null-токен — истёк', () => {
    expect(isTokenExpired(null)).toBe(true);
  });

  it('мусорный токен без трёх частей — истёк', () => {
    expect(isTokenExpired('not-a-jwt')).toBe(true);
  });

  it('битый base64 в payload — истёк', () => {
    expect(isTokenExpired('h.%%%invalid%%%.s')).toBe(true);
  });

  it('payload без exp — истёк', () => {
    expect(isTokenExpired(makeJwt({ sub: 'u1' }))).toBe(true);
  });

  it('истёк более минуты назад — истёк', () => {
    expect(isTokenExpired(makeJwt({ exp: NOW_SEC - 120 }))).toBe(true);
  });

  it('exp ровно через 60с (на границе margin) — истёк', () => {
    expect(isTokenExpired(makeJwt({ exp: NOW_SEC + 60 }))).toBe(true);
  });

  it('exp через 61с — ещё валиден', () => {
    expect(isTokenExpired(makeJwt({ exp: NOW_SEC + 61 }))).toBe(false);
  });
});

describe('isTokenFullyExpired (без margin)', () => {
  it('null и мусор — истёк', () => {
    expect(isTokenFullyExpired(null)).toBe(true);
    expect(isTokenFullyExpired('junk')).toBe(true);
  });

  it('exp в будущем — не истёк', () => {
    expect(isTokenFullyExpired(makeJwt({ exp: NOW_SEC + 1 }))).toBe(false);
  });

  it('exp ровно сейчас — истёк (>=)', () => {
    expect(isTokenFullyExpired(makeJwt({ exp: NOW_SEC }))).toBe(true);
  });
});

describe('getTokenTimeRemaining', () => {
  it('null — -1', () => {
    expect(getTokenTimeRemaining(null)).toBe(-1);
  });

  it('payload без exp — -1', () => {
    expect(getTokenTimeRemaining(makeJwt({ sub: 'u1' }))).toBe(-1);
  });

  it('возвращает секунды до exp минус margin', () => {
    expect(getTokenTimeRemaining(makeJwt({ exp: NOW_SEC + 360 }))).toBe(300);
  });

  it('отрицательное значение при просроченном токене', () => {
    expect(getTokenTimeRemaining(makeJwt({ exp: NOW_SEC - 30 }))).toBe(-90);
  });
});

describe('getTokenExpiryDate', () => {
  it('null и мусор — null', () => {
    expect(getTokenExpiryDate(null)).toBeNull();
    expect(getTokenExpiryDate('junk')).toBeNull();
  });

  it('без exp — null', () => {
    expect(getTokenExpiryDate(makeJwt({ sub: 'u1' }))).toBeNull();
  });

  it('возвращает Date из exp (секунды → мс)', () => {
    expect(getTokenExpiryDate(makeJwt({ exp: NOW_SEC + 100 }))?.getTime()).toBe(
      (NOW_SEC + 100) * 1000
    );
  });
});

describe('isTemporaryPasswordToken', () => {
  it('null — false', () => {
    expect(isTemporaryPasswordToken(null)).toBe(false);
  });

  it('мусорный токен — false', () => {
    expect(isTemporaryPasswordToken('junk')).toBe(false);
  });

  it('temporary_password: true — true', () => {
    expect(
      isTemporaryPasswordToken(makeJwt({ exp: NOW_SEC + 100, temporary_password: true }))
    ).toBe(true);
  });

  it('temporary_password отсутствует — false', () => {
    expect(isTemporaryPasswordToken(makeJwt({ exp: NOW_SEC + 100 }))).toBe(false);
  });

  it('temporary_password: false — false', () => {
    expect(
      isTemporaryPasswordToken(makeJwt({ exp: NOW_SEC + 100, temporary_password: false }))
    ).toBe(false);
  });
});
