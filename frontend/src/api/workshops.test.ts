import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchDevicesTopology, fetchUnitsTopology, fetchWorkshopsTopology } from './workshops';

const { apiFetchMock } = vi.hoisted(() => ({
  apiFetchMock: vi.fn(),
}));

vi.mock('./client', () => ({
  apiFetch: apiFetchMock,
  apiFetchJson: vi.fn(),
  getServerErrorMessage: vi.fn(),
}));

const API_BASE = process.env.VITE_API_BASE ?? '';

function topologyResponse(
  status: number,
  body: unknown,
  headers?: Record<string, string>
): Response {
  return new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('fetchWorkshopsTopology', () => {
  const payload = [{ id: 3, name: 'Цех упаковки', totalUnits: 12 }];

  it('GET topology — URL, data и etag из заголовка', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(200, payload, { ETag: '"abc123"' }));
    const result = await fetchWorkshopsTopology();
    expect(apiFetchMock.mock.calls[0][0]).toBe(`${API_BASE}/api/v1.0.0/workshops/topology`);
    expect(result.data).toEqual(payload);
    expect(result.etag).toBe('"abc123"');
  });

  it('известный ETag шлётся в If-None-Match', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(200, payload));
    await fetchWorkshopsTopology(undefined, '"old"');
    const [, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)['If-None-Match']).toBe('"old"');
  });

  it('304 → data null, etag равен известному', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(304, null));
    const result = await fetchWorkshopsTopology(undefined, '"cached"');
    expect(result.data).toBeNull();
    expect(result.etag).toBe('"cached"');
  });

  it('500 → HttpError', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(500, {}));
    await expect(fetchWorkshopsTopology()).rejects.toMatchObject({ status: 500 });
  });

  it('невалидный ответ сервера — ZodError', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(200, [{ id: -1 }]));
    await expect(fetchWorkshopsTopology()).rejects.toThrow();
  });
});

describe('fetchUnitsTopology', () => {
  it('URL содержит workshopId', async () => {
    apiFetchMock.mockResolvedValue(
      topologyResponse(200, [{ id: 'Line', workshopId: 5, unit: 'Линия 1' }])
    );
    const result = await fetchUnitsTopology(5);
    expect(apiFetchMock.mock.calls[0][0]).toBe(`${API_BASE}/api/v1.0.0/workshops/5/units/topology`);
    expect(result.data).toEqual([{ id: 'Line', workshopId: 5, unit: 'Линия 1' }]);
  });

  it('AbortSignal пробрасывается', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(200, []));
    const signal = new AbortController().signal;
    await fetchUnitsTopology(5, signal);
    const [, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.signal).toBe(signal);
  });
});

describe('fetchDevicesTopology', () => {
  const payload = {
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
    typeNames: {},
  };

  it('URL содержит workshopId и unitId, дефолты groups/deviceMeta', async () => {
    apiFetchMock.mockResolvedValue(topologyResponse(200, payload));
    const result = await fetchDevicesTopology(5, 'Line');
    expect(apiFetchMock.mock.calls[0][0]).toBe(
      `${API_BASE}/api/v1.0.0/workshops/5/units/Line/devices/topology`
    );
    expect(result.data?.groups).toEqual([]);
    expect(result.data?.deviceMeta).toEqual({});
  });
});
