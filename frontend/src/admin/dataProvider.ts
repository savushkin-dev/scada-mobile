/* eslint-disable @typescript-eslint/no-explicit-any */
import { API_BASE } from '../config';
import { apiFetch } from '../api/client';
import type { DataProvider } from 'react-admin';

const baseUrl = `${API_BASE}/api/v1.0.0/admin`;

/**
 * Кастомный HTTP-клиент для React Admin.
 * Использует apiFetch для Bearer-токена + auto-refresh,
 * но адаптирует ответ под формат, ожидаемый simple-rest.
 */
const httpClient = (url: string, options: RequestInit = {}) => {
  return apiFetch(url, options).then(async (response) => {
    const text = await response.text();
    const json = text ? JSON.parse(text) : null;
    return {
      headers: response.headers,
      json,
      status: response.status,
    };
  });
};

/**
 * Кастомный dataProvider для React Admin + Spring Boot backend.
 *
 * Особенности:
 *   - getList:    преобразует RA-пагинацию в Spring Pageable (page, size, sort)
 *   - getMany:    делает параллельные getOne (Spring Data не поддерживает ?id=1&id=2)
 *   - create:     POST /{resource}, возвращает созданную сущность
 *   - update:     PUT /{resource}/{id}, возвращает обновлённую сущность
 *   - delete:     DELETE /{resource}/{id}
 */
export const dataProvider: DataProvider = {
  getList: async (resource, params) => {
    const { page, perPage } = params.pagination || { page: 1, perPage: 10 };
    const { field, order } = params.sort || { field: 'id', order: 'ASC' };

    const query = new URLSearchParams();
    query.set('page', String(page - 1)); // Spring: 0-based
    query.set('size', String(perPage));
    query.set('sort', `${field},${order.toLowerCase() === 'desc' ? 'desc' : 'asc'}`);

    const url = `${baseUrl}/${resource}?${query.toString()}`;

    const { headers, json } = await httpClient(url, {
      headers: new Headers({
        Range: `${resource}=${(page - 1) * perPage}-${page * perPage - 1}`,
      }),
    });

    let data = json.content ?? json;
    let total = 0;

    if (headers.has('content-range')) {
      const contentRange = headers.get('content-range')!;
      total = parseInt(contentRange.split('/').pop() || '0', 10);
    } else {
      // Fallback для ручных контроллеров, которые не возвращают Content-Range
      total = json.totalElements ?? data.length ?? 0;
    }

    if (resource === 'users') {
      const [assignmentsRes, unitsRes] = await Promise.all([
        httpClient(`${baseUrl}/user-assignments?page=0&size=1000&sort=id,asc`),
        httpClient(`${baseUrl}/units?page=0&size=1000&sort=id,asc`),
      ]);
      const assignments = assignmentsRes.json.content ?? [];
      const units = unitsRes.json.content ?? [];

      data = data.map((user: any) => {
        const userAssignments = assignments.filter((a: any) => a.userId === user.id && a.active);
        const unitNames = userAssignments
          .map((a: any) => {
            const unit = units.find((u: any) => u.id === a.unitId);
            return unit?.name ?? a.unitId;
          })
          .join(', ');

        return {
          ...user,
          assignments: userAssignments,
          unitNames,
        };
      });
    }

    return { data, total };
  },

  getOne: (resource, params) => {
    const url = `${baseUrl}/${resource}/${encodeURIComponent(params.id)}`;
    return httpClient(url).then(async ({ json }) => {
      const data = json;
      if (resource === 'units') {
        const devicesUrl = `${baseUrl}/devices?unitId=${params.id}&page=0&size=1000&sort=id,asc`;
        const devicesRes = await httpClient(devicesUrl);
        const devices = devicesRes.json.content ?? [];
        data.catalogIds = devices.map((d: any) => d.catalogId);
      }
      if (resource === 'users') {
        const assignmentsUrl = `${baseUrl}/user-assignments?userId=${params.id}&page=0&size=1000&sort=id,asc`;
        const assignmentsRes = await httpClient(assignmentsUrl);
        const assignments = assignmentsRes.json.content ?? [];
        data.unitIds = assignments
          .filter((a: any) => a.active && a.userId === params.id)
          .map((a: any) => a.unitId);
      }
      return { data };
    });
  },

  getMany: (resource, params) => {
    // Spring Data JPA не поддерживает batch GET по ?id=1&id=2
    // Делаем параллельные getOne запросы
    const ids = params.ids ?? [];
    return Promise.all(
      ids.map((id) =>
        httpClient(`${baseUrl}/${resource}/${encodeURIComponent(id)}`).then(({ json }) => json)
      )
    ).then((data) => ({ data }));
  },

  getManyReference: (resource, params) => {
    const { page, perPage } = params.pagination || { page: 1, perPage: 10 };
    const { field, order } = params.sort || { field: 'id', order: 'ASC' };

    const query = new URLSearchParams();
    query.set('page', String(page - 1));
    query.set('size', String(perPage));
    query.set('sort', `${field},${order.toLowerCase() === 'desc' ? 'desc' : 'asc'}`);
    if (params.target && params.id !== undefined) {
      query.set(params.target, String(params.id));
    }

    const url = `${baseUrl}/${resource}?${query.toString()}`;
    return httpClient(url, {
      headers: new Headers({
        Range: `${resource}=${(page - 1) * perPage}-${page * perPage - 1}`,
      }),
    }).then(({ headers, json }) => {
      const contentRange = headers.get('content-range');
      const total = contentRange
        ? parseInt(contentRange.split('/').pop() || '0', 10)
        : (json.totalElements ?? json.length ?? 0);
      return {
        data: json.content ?? json,
        total,
      };
    });
  },

  create: (resource, params) => {
    const url = `${baseUrl}/${resource}`;
    return httpClient(url, {
      method: 'POST',
      body: JSON.stringify(params.data),
      headers: new Headers({ 'Content-Type': 'application/json' }),
    }).then(({ json }) => ({ data: json }));
  },

  update: (resource, params) => {
    const url = `${baseUrl}/${resource}/${encodeURIComponent(params.id)}`;
    return httpClient(url, {
      method: 'PUT',
      body: JSON.stringify(params.data),
      headers: new Headers({ 'Content-Type': 'application/json' }),
    }).then(({ json }) => ({ data: json }));
  },

  updateMany: (resource, params) => {
    return Promise.all(
      params.ids.map((id) =>
        httpClient(`${baseUrl}/${resource}/${encodeURIComponent(id)}`, {
          method: 'PUT',
          body: JSON.stringify(params.data),
          headers: new Headers({ 'Content-Type': 'application/json' }),
        })
      )
    ).then(() => ({ data: params.ids }));
  },

  delete: (resource, params) => {
    const url = `${baseUrl}/${resource}/${encodeURIComponent(params.id)}`;
    return httpClient(url, { method: 'DELETE' }).then(() => ({
      data: { id: params.id },
    })) as Promise<any>;
  },

  deleteMany: (resource, params) => {
    return Promise.all(
      params.ids.map((id) =>
        httpClient(`${baseUrl}/${resource}/${encodeURIComponent(id)}`, {
          method: 'DELETE',
        })
      )
    ).then(() => ({ data: params.ids }));
  },
};
