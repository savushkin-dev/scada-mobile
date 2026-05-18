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
  getList: (resource, params) => {
    const { page, perPage } = params.pagination || { page: 1, perPage: 10 };
    const { field, order } = params.sort || { field: 'id', order: 'ASC' };

    const query = new URLSearchParams();
    query.set('page', String(page - 1)); // Spring: 0-based
    query.set('size', String(perPage));
    query.set('sort', `${field},${order.toLowerCase() === 'desc' ? 'desc' : 'asc'}`);

    const url = `${baseUrl}/${resource}?${query.toString()}`;

    return httpClient(url, {
      headers: new Headers({
        Range: `${resource}=${(page - 1) * perPage}-${page * perPage - 1}`,
      }),
    }).then(({ headers, json }) => {
      if (!headers.has('content-range')) {
        throw new Error(
          'The Content-Range header is missing in the HTTP Response. ' +
            'If you are using CORS, did you declare Content-Range in the Access-Control-Expose-Headers header?'
        );
      }
      const contentRange = headers.get('content-range')!;
      const total = parseInt(contentRange.split('/').pop() || '0', 10);
      return {
        data: json.content ?? json,
        total,
      };
    });
  },

  getOne: (resource, params) => {
    const url = `${baseUrl}/${resource}/${encodeURIComponent(params.id)}`;
    return httpClient(url).then(({ json }) => ({ data: json }));
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
      data: { id: params.id } as Record<string, unknown>,
    }));
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
