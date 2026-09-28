import { beforeEach, describe, expect, it, vi } from 'vitest';
import { changePassword, loginUser, logoutUser, refreshAccessToken } from './auth';
import { AuthSessionExpiredError, NetworkUnavailableError } from '../errors/AppError';

const { getRefreshTokenMock, setTokensMock, apiFetchMock } = vi.hoisted(() => ({
  getRefreshTokenMock: vi.fn(),
  setTokensMock: vi.fn(),
  apiFetchMock: vi.fn(),
}));

vi.mock('../auth/session', () => ({
  getRefreshToken: getRefreshTokenMock,
  setTokens: setTokensMock,
}));

vi.mock('./client', () => ({
  apiFetch: apiFetchMock,
  apiFetchJson: vi.fn(),
}));

const API_BASE = process.env.VITE_API_BASE ?? '';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  getRefreshTokenMock.mockReturnValue('refresh-1');
});

describe('loginUser', () => {
  it('POST /auth/login: URL, method, headers, body; маппинг ответа', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(200, {
        userId: 42,
        role: 'TECH',
        temporaryPassword: true,
        accessToken: 'a1',
        refreshToken: 'r1',
      })
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await loginUser({ workerCode: '1024', password: 'pass123' });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE}/api/v1.0.0/auth/login`);
    expect(init.method).toBe('POST');
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json');
    expect(JSON.parse(String(init.body))).toEqual({ workerCode: '1024', password: 'pass123' });

    expect(result).toEqual({
      userId: '42',
      role: 'TECH',
      temporaryPassword: true,
      accessToken: 'a1',
      refreshToken: 'r1',
    });
  });

  it('userId приходит строкой — сохраняется без изменений', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse(200, { userId: 'user-7', role: '', accessToken: 'a', refreshToken: 'r' })
        )
    );
    const result = await loginUser({ workerCode: '1', password: 'p12345' });
    expect(result.userId).toBe('user-7');
    expect(result.temporaryPassword).toBe(false);
  });

  it('!ok с телом {message} — ошибка "status|message"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(401, { message: 'Неверный пароль' }))
    );
    await expect(loginUser({ workerCode: '1', password: 'p12345' })).rejects.toThrow(
      '401|Неверный пароль'
    );
  });

  it('нет userId в ответе — ошибка', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(200, { accessToken: 'a', refreshToken: 'r' }))
    );
    await expect(loginUser({ workerCode: '1', password: 'p12345' })).rejects.toThrow(
      'Missing userId in auth response'
    );
  });

  it('нет токенов в ответе — ошибка', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, { userId: 'u' })));
    await expect(loginUser({ workerCode: '1', password: 'p12345' })).rejects.toThrow(
      'Missing tokens in auth response'
    );
  });
});

describe('changePassword', () => {
  it('PUT через apiFetch и сохранение новой пары токенов', async () => {
    apiFetchMock.mockResolvedValue(
      jsonResponse(200, {
        userId: 'u',
        role: 'TECH',
        temporaryPassword: false,
        accessToken: 'a2',
        refreshToken: 'r2',
      })
    );

    const result = await changePassword({ newPassword: 'newpass1' });

    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE}/api/v1.0.0/auth/change-password`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ newPassword: 'newpass1' });
    expect(setTokensMock).toHaveBeenCalledWith('a2', 'r2');
    expect(result.accessToken).toBe('a2');
  });

  it('!ok — ошибка "status|message"', async () => {
    apiFetchMock.mockResolvedValue(jsonResponse(400, { message: 'Слабый пароль' }));
    await expect(changePassword({ newPassword: 'x' })).rejects.toThrow('400|Слабый пароль');
    expect(setTokensMock).not.toHaveBeenCalled();
  });
});

describe('logoutUser', () => {
  it('POST /auth/logout с refresh-токеном в body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal('fetch', fetchMock);

    await logoutUser();

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE}/api/v1.0.0/auth/logout`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ refreshToken: 'refresh-1' });
  });

  it('сетевая ошибка игнорируется — logout работает локально', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')));
    await expect(logoutUser()).resolves.toBeUndefined();
  });
});

describe('refreshAccessToken', () => {
  it('успех: POST /auth/refresh, токены сохранены, новый access возвращён', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(200, { accessToken: 'a-new', refreshToken: 'r-new' }));
    vi.stubGlobal('fetch', fetchMock);

    const token = await refreshAccessToken();

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE}/api/v1.0.0/auth/refresh`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ refreshToken: 'refresh-1' });
    expect(setTokensMock).toHaveBeenCalledWith('a-new', 'r-new');
    expect(token).toBe('a-new');
  });

  it('нет refresh-токена — AuthSessionExpiredError, fetch не вызывается', async () => {
    getRefreshTokenMock.mockReturnValue(null);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(refreshAccessToken()).rejects.toBeInstanceOf(AuthSessionExpiredError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('fetch бросил TypeError — NetworkUnavailableError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')));
    await expect(refreshAccessToken()).rejects.toBeInstanceOf(NetworkUnavailableError);
    expect(setTokensMock).not.toHaveBeenCalled();
  });

  it('401 — AuthSessionExpiredError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {})));
    await expect(refreshAccessToken()).rejects.toBeInstanceOf(AuthSessionExpiredError);
    expect(setTokensMock).not.toHaveBeenCalled();
  });

  it('403 — AuthSessionExpiredError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(403, {})));
    await expect(refreshAccessToken()).rejects.toBeInstanceOf(AuthSessionExpiredError);
  });

  it('500 — ServerUnavailableError со status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(502, {})));
    await expect(refreshAccessToken()).rejects.toMatchObject({ status: 502 });
    expect(setTokensMock).not.toHaveBeenCalled();
  });

  it('200 без токенов в теле — AuthSessionExpiredError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, {})));
    await expect(refreshAccessToken()).rejects.toBeInstanceOf(AuthSessionExpiredError);
    expect(setTokensMock).not.toHaveBeenCalled();
  });
});
