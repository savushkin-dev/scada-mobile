import { beforeEach, describe, expect, it, vi } from 'vitest';

const { refreshAccessTokenMock, clearAllAuthDataMock, getAccessTokenMock } = vi.hoisted(() => ({
  refreshAccessTokenMock: vi.fn(),
  clearAllAuthDataMock: vi.fn(),
  getAccessTokenMock: vi.fn(),
}));

vi.mock('../auth/session', () => ({
  getAccessToken: getAccessTokenMock,
  clearAllAuthData: clearAllAuthDataMock,
}));

vi.mock('./auth', () => ({
  refreshAccessToken: refreshAccessTokenMock,
}));

function jsonResponse(status: number, body: unknown, headers?: Record<string, string>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  getAccessTokenMock.mockReturnValue('old-token');
});

async function importClient() {
  // Динамический импорт: vi.resetModules() даёт свежий модуль client.ts,
  // а ошибки берём из той же копии модулей — instanceof корректен.
  const [client, appErrors] = await Promise.all([import('./client'), import('../errors/AppError')]);
  return { ...client, ...appErrors };
}

describe('apiFetch', () => {
  it('200 — ответ возвращается как есть, Authorization из хранилища', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    const { apiFetch } = await importClient();
    const resp = await apiFetch('/api/test');

    expect(resp.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/test');
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer old-token');
  });

  it('JSON body без Content-Type — ставится application/json', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal('fetch', fetchMock);

    const { apiFetch } = await importClient();
    await apiFetch('/api/test', { method: 'POST', body: JSON.stringify({ a: 1 }) });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json');
  });

  it('не-401 — возвращается без refresh, даже 500', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(500, {}));
    vi.stubGlobal('fetch', fetchMock);

    const { apiFetch } = await importClient();
    const resp = await apiFetch('/api/test');

    expect(resp.status).toBe(500);
    expect(refreshAccessTokenMock).not.toHaveBeenCalled();
  });

  it('401 → refresh успешен → retry с новым Authorization', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, {}))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));
    vi.stubGlobal('fetch', fetchMock);
    refreshAccessTokenMock.mockResolvedValue('new-token');

    const { apiFetch } = await importClient();
    const resp = await apiFetch('/api/test');

    expect(resp.status).toBe(200);
    expect(refreshAccessTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [, retryInit] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(new Headers(retryInit.headers).get('Authorization')).toBe('Bearer new-token');
  });

  it('401 → refresh бросил обычную ошибку → clearAllAuthData + AuthSessionExpiredError', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(401, {}));
    vi.stubGlobal('fetch', fetchMock);
    refreshAccessTokenMock.mockRejectedValue(new Error('HTTP 401'));

    const { apiFetch, AuthSessionExpiredError } = await importClient();

    await expect(apiFetch('/api/test')).rejects.toBeInstanceOf(AuthSessionExpiredError);
    expect(clearAllAuthDataMock).toHaveBeenCalledTimes(1);
  });

  it('401 → refresh бросил NetworkUnavailableError → токены НЕ чистятся, ошибка прокинута', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(401, {}));
    vi.stubGlobal('fetch', fetchMock);
    const { apiFetch, NetworkUnavailableError } = await importClient();
    const networkError = new NetworkUnavailableError();
    refreshAccessTokenMock.mockRejectedValue(networkError);

    await expect(apiFetch('/api/test')).rejects.toBe(networkError);
    expect(clearAllAuthDataMock).not.toHaveBeenCalled();
  });

  it('401 → refresh бросил ServerUnavailableError → токены НЕ чистятся', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(401, {}));
    vi.stubGlobal('fetch', fetchMock);
    const { apiFetch, ServerUnavailableError } = await importClient();
    const serverError = new ServerUnavailableError(502);
    refreshAccessTokenMock.mockRejectedValue(serverError);

    await expect(apiFetch('/api/test')).rejects.toBe(serverError);
    expect(clearAllAuthDataMock).not.toHaveBeenCalled();
  });

  it('параллельные два 401 → refresh вызван один раз, оба retry', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const auth = new Headers(init?.headers).get('Authorization');
      if (auth === 'Bearer old-token') {
        return Promise.resolve(jsonResponse(401, {}));
      }
      return Promise.resolve(jsonResponse(200, { url }));
    });
    vi.stubGlobal('fetch', fetchMock);
    refreshAccessTokenMock.mockResolvedValue('new-token');

    const { apiFetch } = await importClient();
    const [r1, r2] = await Promise.all([apiFetch('/api/a'), apiFetch('/api/b')]);

    expect(refreshAccessTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(4); // 2 первичных + 2 retry
    expect(r1.status).toBe(200);
    expect(r2.status).toBe(200);
    await expect(r1.json()).resolves.toEqual({ url: '/api/a' });
    await expect(r2.json()).resolves.toEqual({ url: '/api/b' });
  });
});

describe('apiFetchJson', () => {
  it('ok — возвращает распарсенный JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, { hello: 'world' })));
    const { apiFetchJson } = await importClient();
    await expect(apiFetchJson('/api/test')).resolves.toEqual({ hello: 'world' });
  });

  it('!ok с телом {message} — HttpError со status и serverMessage', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(409, { status: 409, message: 'Конфликт версий' }))
    );
    const { apiFetchJson } = await importClient();

    try {
      await apiFetchJson('/api/test');
      expect.unreachable('должно бросить');
    } catch (e) {
      expect(e).toBeInstanceOf(Error);
      expect((e as Error & { name: string }).name).toBe('HttpError');
      expect((e as unknown as { status: number }).status).toBe(409);
      expect((e as unknown as { serverMessage: string }).serverMessage).toBe('Конфликт версий');
    }
  });

  it('!ok с не-JSON телом — HttpError без serverMessage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('oops', { status: 500 })));
    const { apiFetchJson } = await importClient();

    try {
      await apiFetchJson('/api/test');
      expect.unreachable('должно бросить');
    } catch (e) {
      expect((e as Error & { name: string }).name).toBe('HttpError');
      expect((e as unknown as { status: number }).status).toBe(500);
      expect((e as unknown as { serverMessage?: string }).serverMessage).toBeUndefined();
    }
  });
});

describe('getServerErrorMessage', () => {
  it('HttpError с serverMessage — возвращает его', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(409, { message: 'Конфликт версий' }))
    );
    const { apiFetchJson, getServerErrorMessage } = await importClient();

    try {
      await apiFetchJson('/api/test');
    } catch (e) {
      expect(getServerErrorMessage(e, 'fallback')).toBe('Конфликт версий');
    }
  });

  it('HttpError без serverMessage — fallback', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(500, { message: 42 })));
    const { apiFetchJson, getServerErrorMessage } = await importClient();

    try {
      await apiFetchJson('/api/test');
    } catch (e) {
      expect(getServerErrorMessage(e, 'fallback')).toBe('fallback');
    }
  });

  it('обычная ошибка — fallback', async () => {
    const { getServerErrorMessage } = await importClient();
    expect(getServerErrorMessage(new Error('x'), 'fallback')).toBe('fallback');
  });

  it('не-Error значение — fallback', async () => {
    const { getServerErrorMessage } = await importClient();
    expect(getServerErrorMessage('строка', 'fallback')).toBe('fallback');
    expect(getServerErrorMessage(null, 'fallback')).toBe('fallback');
  });
});
