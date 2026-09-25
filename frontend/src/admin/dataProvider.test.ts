import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dataProvider } from './dataProvider';

const { apiFetchMock } = vi.hoisted(() => ({
  apiFetchMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  apiFetch: apiFetchMock,
  apiFetchJson: vi.fn(),
  getServerErrorMessage: vi.fn(),
}));

const API_BASE = process.env.VITE_API_BASE ?? '';
const BASE = `${API_BASE}/api/v1.0.0/admin`;

/** Хелпер: регистрирует ответ по подстроке URL (FIFO в пределах одного совпадения). */
function mockResponse(
  urlPart: string,
  status: number,
  body: unknown,
  headers?: Record<string, string>
) {
  apiFetchMock.mockImplementation((url: string) => {
    if (String(url).includes(urlPart)) {
      return Promise.resolve(
        new Response(body === null ? null : JSON.stringify(body), {
          status,
          headers: { 'Content-Type': 'application/json', ...headers },
        })
      );
    }
    return Promise.reject(new Error(`Unexpected URL: ${String(url)}`));
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('dataProvider.getList', () => {
  it('query-string: page (0-based), size, sort=field,asc|desc, фильтры q и f.<field>', async () => {
    mockResponse(
      '/workshops',
      200,
      { content: [{ id: 1 }], totalElements: 1 },
      {
        'Content-Range': 'workshops 0-0/1',
      }
    );

    const result = await dataProvider.getList('workshops', {
      pagination: { page: 2, perPage: 10 },
      sort: { field: 'name', order: 'DESC' },
      filter: { q: 'иван', f: { active: 'true' } },
    });

    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/v1.0.0/admin/workshops');
    expect(parsed.searchParams.get('page')).toBe('1');
    expect(parsed.searchParams.get('size')).toBe('10');
    expect(parsed.searchParams.get('sort')).toBe('name,desc');
    expect(parsed.searchParams.get('q')).toBe('иван');
    expect(parsed.searchParams.get('f.active')).toBe('true');
    expect(new Headers(init.headers).get('Range')).toBe('workshops=10-19');

    expect(result.data).toEqual([{ id: 1 }]);
    expect(result.total).toBe(1);
  });

  it('total из totalElements при отсутствии Content-Range', async () => {
    mockResponse('/roles', 200, { content: [{ id: 1 }, { id: 2 }], totalElements: 7 });

    const result = await dataProvider.getList('roles', {
      pagination: { page: 1, perPage: 10 },
      sort: { field: 'id', order: 'ASC' },
      filter: {},
    });
    expect(result.total).toBe(7);
    expect(result.data).toHaveLength(2);
  });

  it('resource units: обогащение deviceNames через devices + device-catalog', async () => {
    apiFetchMock.mockImplementation((url: string) => {
      const u = String(url);
      if (u.includes('/admin/units?')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ content: [{ id: 10, name: 'Линия 1' }], totalElements: 1 }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          )
        );
      }
      if (u.includes('/admin/devices?')) {
        return Promise.resolve(
          new Response(JSON.stringify({ content: [{ id: 1, unitId: 10, catalogId: 5 }] }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (u.includes('/admin/device-catalog?')) {
        return Promise.resolve(
          new Response(JSON.stringify({ content: [{ id: 5, name: 'Принтер', code: 'MARK' }] }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unexpected URL: ${u}`));
    });

    const result = await dataProvider.getList('units', {
      pagination: { page: 1, perPage: 10 },
      sort: { field: 'id', order: 'ASC' },
      filter: {},
    });

    expect(result.data).toEqual([{ id: 10, name: 'Линия 1', deviceNames: ['Принтер'] }]);
    expect(result.total).toBe(1);
  });
});

describe('dataProvider.getOne', () => {
  it('units: маппинг catalogIds и deviceLinks с дефолтами', async () => {
    apiFetchMock.mockImplementation((url: string) => {
      const u = String(url);
      if (u === `${BASE}/units/10`) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 10, name: 'Линия 1' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (u.includes('/admin/devices?unitId=10')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              content: [
                {
                  id: 1,
                  catalogId: 5,
                  displayName: 'Переименованный',
                  groupLabel: 'Поток 1',
                  displayOrder: 2,
                  showCounters: true,
                  scadaPrefix: 'P1_',
                  hidden: false,
                },
                { id: 2, catalogId: 6 },
              ],
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          )
        );
      }
      return Promise.reject(new Error(`Unexpected URL: ${u}`));
    });

    const result = await dataProvider.getOne('units', { id: 10 });

    expect(result.data.catalogIds).toEqual([5, 6]);
    expect(result.data.deviceLinks).toEqual([
      {
        id: 1,
        catalogId: 5,
        displayName: 'Переименованный',
        groupLabel: 'Поток 1',
        displayOrder: 2,
        showCounters: true,
        scadaPrefix: 'P1_',
        hidden: false,
      },
      {
        id: 2,
        catalogId: 6,
        displayName: '',
        groupLabel: '',
        displayOrder: 0,
        showCounters: false,
        scadaPrefix: '',
        hidden: false,
      },
    ]);
  });

  it('id кодируется через encodeURIComponent', async () => {
    mockResponse('/roles/ADM%2F1', 200, { id: 'ADM/1' });
    await dataProvider.getOne('roles', { id: 'ADM/1' });
    const [url] = apiFetchMock.mock.calls[0] as [string];
    expect(url).toBe(`${BASE}/roles/ADM%2F1`);
  });
});

describe('dataProvider.getMany', () => {
  it('параллельные getOne по каждому id, маппинг массива', async () => {
    apiFetchMock.mockImplementation((url: string) => {
      const u = String(url);
      if (u === `${BASE}/roles/1`) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 1 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (u === `${BASE}/roles/2`) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 2 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unexpected URL: ${u}`));
    });

    const result = await dataProvider.getMany('roles', { ids: [1, 2] });
    expect(result.data).toEqual([{ id: 1 }, { id: 2 }]);
    expect(apiFetchMock).toHaveBeenCalledTimes(2);
  });
});

describe('dataProvider.create', () => {
  it('POST body — сериализованный params.data, ответ как data', async () => {
    apiFetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: 9, name: 'Новый' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const result = await dataProvider.create('roles', { data: { name: 'Новый' } });

    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${BASE}/roles`);
    expect(init.method).toBe('POST');
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json');
    expect(JSON.parse(String(init.body))).toEqual({ name: 'Новый' });
    expect(result.data).toEqual({ id: 9, name: 'Новый' });
  });
});

describe('dataProvider.update', () => {
  it('notifications/read-all: POST /notifications/read-all', async () => {
    apiFetchMock.mockResolvedValue(
      new Response(null, { status: 200, headers: { 'Content-Type': 'application/json' } })
    );

    const result = await dataProvider.update('notifications', {
      id: 'read-all',
      data: {},
      previousData: {},
    });

    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${BASE}/notifications/read-all`);
    expect(init.method).toBe('POST');
    expect(result.data).toEqual({ id: 'read-all' });
  });

  it('notifications/{id}: POST /notifications/{id}/read', async () => {
    apiFetchMock.mockResolvedValue(
      new Response(null, { status: 200, headers: { 'Content-Type': 'application/json' } })
    );

    await dataProvider.update('notifications', { id: 5, data: {}, previousData: {} });

    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${BASE}/notifications/5/read`);
    expect(init.method).toBe('POST');
  });

  it('units: body без deviceLinks; PUT /devices/{id} с displayName: null', async () => {
    apiFetchMock.mockImplementation((url: string) => {
      const u = String(url);
      if (u === `${BASE}/units/10`) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 10 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (u === `${BASE}/devices/1`) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 1 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unexpected URL: ${u}`));
    });

    const result = await dataProvider.update('units', {
      id: 10,
      data: {
        name: 'Линия 1',
        catalogIds: [5],
        deviceLinks: [
          {
            id: 1,
            catalogId: 5,
            groupLabel: 'Поток 1',
            displayOrder: 1,
            showCounters: true,
            scadaPrefix: '',
            hidden: false,
          },
          // Снятая связь (catalogId не в catalogIds) — PUT не должен уйти
          { id: 2, catalogId: 7, groupLabel: '', displayOrder: 0, showCounters: false },
        ],
      },
      previousData: {},
    });

    // Основной PUT units: body без deviceLinks
    const [unitsUrl, unitsInit] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(unitsUrl).toBe(`${BASE}/units/10`);
    expect(unitsInit.method).toBe('PUT');
    const unitsBody = JSON.parse(String(unitsInit.body));
    expect(unitsBody.deviceLinks).toBeUndefined();
    expect(unitsBody.catalogIds).toEqual([5]);

    // Раскладка существующей связи: displayName сброшен в null
    const [devUrl, devInit] = apiFetchMock.mock.calls[1] as [string, RequestInit];
    expect(devUrl).toBe(`${BASE}/devices/1`);
    expect(devInit.method).toBe('PUT');
    expect(JSON.parse(String(devInit.body))).toEqual({
      unitId: 10,
      catalogId: 5,
      displayName: null,
      groupLabel: 'Поток 1',
      displayOrder: 1,
      showCounters: true,
      scadaPrefix: null,
      hidden: false,
    });

    expect(apiFetchMock).toHaveBeenCalledTimes(2);
    expect(result.data).toEqual({ id: 10 });
  });
});

describe('dataProvider.delete / deleteMany / updateMany', () => {
  it('delete: DELETE /{resource}/{id}', async () => {
    apiFetchMock.mockResolvedValue(
      new Response(null, { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
    const result = await dataProvider.delete('roles', { id: 3, previousData: { id: 3 } });
    const [url, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${BASE}/roles/3`);
    expect(init.method).toBe('DELETE');
    expect(result.data).toEqual({ id: 3 });
  });

  it('deleteMany: DELETE по каждому id', async () => {
    apiFetchMock.mockResolvedValue(
      new Response(null, { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
    const result = await dataProvider.deleteMany('roles', { ids: [1, 2] });
    expect(apiFetchMock).toHaveBeenCalledTimes(2);
    expect(result.data).toEqual([1, 2]);
  });

  it('updateMany: PUT по каждому id с общим data', async () => {
    apiFetchMock.mockResolvedValue(
      new Response(null, { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
    const result = await dataProvider.updateMany('roles', {
      ids: [1, 2],
      data: { active: false },
    });
    expect(apiFetchMock).toHaveBeenCalledTimes(2);
    const [, init] = apiFetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual({ active: false });
    expect(result.data).toEqual([1, 2]);
  });
});

describe('dataProvider.getManyReference', () => {
  it('query содержит target/id, total из Content-Range', async () => {
    mockResponse(
      '/devices?',
      200,
      { content: [{ id: 1 }] },
      {
        'Content-Range': 'devices 0-0/5',
      }
    );

    const result = await dataProvider.getManyReference('devices', {
      pagination: { page: 1, perPage: 10 },
      sort: { field: 'id', order: 'ASC' },
      filter: {},
      target: 'unitId',
      id: 10,
    });

    const [url] = apiFetchMock.mock.calls[0] as [string];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.searchParams.get('unitId')).toBe('10');
    expect(parsed.searchParams.get('page')).toBe('0');
    expect(parsed.searchParams.get('size')).toBe('10');
    expect(result.total).toBe(5);
    expect(result.data).toEqual([{ id: 1 }]);
  });
});
