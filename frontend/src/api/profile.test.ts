import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchNotificationSettings, fetchUserProfile, updateNotificationSetting } from './profile';

const { apiFetchJsonMock, apiFetchMock } = vi.hoisted(() => ({
  apiFetchJsonMock: vi.fn(),
  apiFetchMock: vi.fn(),
}));

vi.mock('./client', () => ({
  apiFetch: apiFetchMock,
  apiFetchJson: apiFetchJsonMock,
  getServerErrorMessage: vi.fn(),
}));

const API_BASE = process.env.VITE_API_BASE ?? '';

const profile = {
  fullName: 'Иван Петров',
  role: 'TECH',
  workerCode: '1024',
  assignedUnits: [{ unitId: 'Line', unitName: 'Линия 1', printsrvInstanceId: 'srv-1' }],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('fetchUserProfile', () => {
  it('GET /users/me — URL и маппинг ответа', async () => {
    apiFetchJsonMock.mockResolvedValue(profile);
    const result = await fetchUserProfile();
    expect(apiFetchJsonMock.mock.calls[0][0]).toBe(`${API_BASE}/api/v1.0.0/users/me`);
    expect(result).toEqual(profile);
  });

  it('AbortSignal пробрасывается в options', async () => {
    apiFetchJsonMock.mockResolvedValue(profile);
    const signal = new AbortController().signal;
    await fetchUserProfile(signal);
    const [, init] = apiFetchJsonMock.mock.calls[0] as [string, RequestInit];
    expect(init.signal).toBe(signal);
  });

  it('невалидный ответ — ZodError', async () => {
    apiFetchJsonMock.mockResolvedValue({ fullName: 'Иван' });
    await expect(fetchUserProfile()).rejects.toThrow();
  });
});

describe('fetchNotificationSettings', () => {
  it('GET /notifications/settings — URL и маппинг', async () => {
    const settings = [
      { unitId: 'Line', unitName: 'Линия 1', techEnabled: true, masterEnabled: false },
    ];
    apiFetchJsonMock.mockResolvedValue(settings);
    const result = await fetchNotificationSettings();
    expect(apiFetchJsonMock.mock.calls[0][0]).toBe(`${API_BASE}/api/v1.0.0/notifications/settings`);
    expect(result).toEqual(settings);
  });

  it('не массив — ZodError', async () => {
    apiFetchJsonMock.mockResolvedValue({ unitId: 'Line' });
    await expect(fetchNotificationSettings()).rejects.toThrow();
  });
});

describe('updateNotificationSetting', () => {
  it('PUT /notifications/settings: method и сериализация body', async () => {
    apiFetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const payload = { unitId: 'Line', techEnabled: true, masterEnabled: false };
    await updateNotificationSetting(payload);

    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE}/api/v1.0.0/notifications/settings`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual(payload);
  });

  it('!ok — HttpError со статусом', async () => {
    apiFetchMock.mockResolvedValue(new Response(null, { status: 409 }));
    await expect(
      updateNotificationSetting({ unitId: 'Line', techEnabled: false, masterEnabled: false })
    ).rejects.toMatchObject({ status: 409 });
  });

  it('ok — void, без исключения', async () => {
    apiFetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    await expect(
      updateNotificationSetting({ unitId: 'Line', techEnabled: true, masterEnabled: true })
    ).resolves.toBeUndefined();
  });
});
