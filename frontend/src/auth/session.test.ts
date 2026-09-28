import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearAllAuthData,
  clearStoredRole,
  clearStoredUserId,
  getAccessToken,
  getAuthUserId,
  getInitialUserId,
  getRefreshToken,
  getStoredRole,
  getStoredUserId,
  setAccessToken,
  setStoredRole,
  setStoredUserId,
  setTokens,
} from './session';

/** Минимальный in-memory стаб localStorage для node-окружения. */
function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (k: string) => (map.has(k) ? (map.get(k) as string) : null),
    key: (i: number) => [...map.keys()][i] ?? null,
    removeItem: (k: string) => void map.delete(k),
    setItem: (k: string, v: string) => void map.set(k, String(v)),
  } as Storage;
}

const memoryStorage = createMemoryStorage();

beforeEach(() => {
  memoryStorage.clear();
  vi.stubGlobal('localStorage', memoryStorage);
});

describe('userId', () => {
  it('пустое хранилище → null', () => {
    expect(getStoredUserId()).toBeNull();
    expect(getInitialUserId()).toBeNull();
    expect(getAuthUserId()).toBeNull();
  });

  it('сохранение и чтение', () => {
    setStoredUserId('user-1');
    expect(getStoredUserId()).toBe('user-1');
  });

  it('обрезает пробелы при сохранении и чтении', () => {
    setStoredUserId('  user-2  ');
    expect(getStoredUserId()).toBe('user-2');
  });

  it('пробелы-only не сохраняются', () => {
    setStoredUserId('   ');
    expect(getStoredUserId()).toBeNull();
  });

  it('значение из пробелов в хранилище → null', () => {
    memoryStorage.setItem('scada.userId', '   ');
    expect(getStoredUserId()).toBeNull();
  });

  it('clearStoredUserId удаляет ключ', () => {
    setStoredUserId('user-1');
    clearStoredUserId();
    expect(getStoredUserId()).toBeNull();
  });

  it('getItem бросает (private mode) → null, без исключения', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('denied');
      },
      setItem: () => {
        throw new Error('denied');
      },
      removeItem: () => {
        throw new Error('denied');
      },
    } as unknown as Storage);
    expect(getStoredUserId()).toBeNull();
    expect(getAccessToken()).toBeNull();
    expect(() => setStoredUserId('x')).not.toThrow();
    expect(() => clearAllAuthData()).not.toThrow();
  });
});

describe('токены', () => {
  it('пустое хранилище → null', () => {
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it('setTokens сохраняет оба токена', () => {
    setTokens('access-1', 'refresh-1');
    expect(getAccessToken()).toBe('access-1');
    expect(getRefreshToken()).toBe('refresh-1');
  });

  it('setAccessToken обновляет только access', () => {
    setTokens('access-1', 'refresh-1');
    setAccessToken('access-2');
    expect(getAccessToken()).toBe('access-2');
    expect(getRefreshToken()).toBe('refresh-1');
  });

  it('битое значение в storage читается как есть (строка)', () => {
    memoryStorage.setItem('scada.accessToken', '%%%');
    expect(getAccessToken()).toBe('%%%');
  });
});

describe('роль', () => {
  it('сохранение/чтение/очистка', () => {
    expect(getStoredRole()).toBeNull();
    setStoredRole('ADMIN');
    expect(getStoredRole()).toBe('ADMIN');
    clearStoredRole();
    expect(getStoredRole()).toBeNull();
  });
});

describe('clearAllAuthData', () => {
  it('чистит userId, роль, токены и assignedUnits', () => {
    setStoredUserId('user-1');
    setStoredRole('TECH');
    setTokens('a', 'r');
    memoryStorage.setItem('scada.assignedUnits', '[]');

    clearAllAuthData();

    expect(memoryStorage.getItem('scada.userId')).toBeNull();
    expect(memoryStorage.getItem('scada.role')).toBeNull();
    expect(memoryStorage.getItem('scada.assignedUnits')).toBeNull();
    expect(memoryStorage.getItem('scada.accessToken')).toBeNull();
    expect(memoryStorage.getItem('scada.refreshToken')).toBeNull();
    expect(getStoredUserId()).toBeNull();
    expect(getStoredRole()).toBeNull();
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });
});
