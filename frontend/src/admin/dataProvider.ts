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
  getList: (resource, params) => {
    const { page, perPage } = params.pagination || { page: 1, perPage: 10 };
    const { field, order } = params.sort || { field: 'id', order: 'ASC' };

    const query = new URLSearchParams();
    query.set('page', String(page - 1)); // Spring: 0-based
    query.set('size', String(perPage));
    query.set('sort', `${field},${order.toLowerCase() === 'desc' ? 'desc' : 'asc'}`);

    // Пробрасываем простые фильтры react-admin в query-параметры Spring
    if (params.filter) {
      Object.entries(params.filter).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          query.set(key, String(value));
        }
      });
    }

    const url = `${baseUrl}/${resource}?${query.toString()}`;

    return httpClient(url, {
      headers: new Headers({
        Range: `${resource}=${(page - 1) * perPage}-${page * perPage - 1}`,
      }),
    }).then(async ({ headers, json }) => {
      let data = json.content ?? json;
      let total: number;

      const contentRange = headers.get('content-range');
      if (contentRange) {
        total = parseInt(contentRange.split('/').pop() || '0', 10);
      } else {
        // Fallback для ручных контроллеров, которые не возвращают Content-Range
        total = json.totalElements ?? data.length ?? 0;
      }

      if (resource === 'notifications') {
        // /admin/notifications возвращает плоский массив непрочитанных уведомлений
        return { data, total: data.length };
      }

      if (resource === 'users') {
        const [assignmentsRes, unitsRes, settingsRes] = await Promise.all([
          httpClient(`${baseUrl}/user-assignments?page=0&size=1000&sort=id,asc`),
          httpClient(`${baseUrl}/units?page=0&size=1000&sort=id,asc`),
          httpClient(`${baseUrl}/user-notification-settings?page=0&size=1000&sort=id,asc`),
        ]);
        const assignments = assignmentsRes.json.content ?? [];
        const units = unitsRes.json.content ?? [];
        const settings = settingsRes.json.content ?? [];

        data = data.map((user: any) => {
          const userAssignments = assignments.filter((a: any) => a.userId === user.id && a.active);
          const unitNames = userAssignments.map((a: any) => {
            const unit = units.find((u: any) => u.id === a.unitId);
            return unit?.name ?? String(a.unitId);
          });

          const userSettings = settings.filter((s: any) => s.userId === user.id && s.active);
          const incidentCount = userSettings.filter(
            (s: any) => s.incidentNotificationsEnabled
          ).length;
          const callCount = userSettings.filter(
            (s: any) => s.androidCallNotificationsEnabled
          ).length;

          return {
            ...user,
            assignments: userAssignments,
            unitNames,
            notificationSettingsCount: userSettings.length,
            incidentNotificationsCount: incidentCount,
            callNotificationsCount: callCount,
          };
        });
      }

      if (resource === 'units') {
        const [devicesRes, catalogRes] = await Promise.all([
          httpClient(`${baseUrl}/devices?page=0&size=1000&sort=id,asc`),
          httpClient(`${baseUrl}/device-catalog?page=0&size=1000&sort=id,asc`),
        ]);
        const devices = devicesRes.json.content ?? [];
        const catalogs = catalogRes.json.content ?? [];

        data = data.map((unit: any) => {
          const unitCatalogIds = devices
            .filter((d: any) => d.unitId === unit.id)
            .map((d: any) => d.catalogId);
          const deviceNames = unitCatalogIds.map((catalogId: any) => {
            const catalog = catalogs.find((c: any) => c.id === catalogId);
            return catalog?.displayName ?? catalog?.code ?? String(catalogId);
          });

          return {
            ...unit,
            deviceNames,
          };
        });
      }

      return { data, total };
    });
  },

  getOne: (resource, params) => {
    const url = `${baseUrl}/${resource}/${encodeURIComponent(params.id)}`;
    return httpClient(url).then(async ({ json }) => {
      let data = json;

      if (resource === 'users') {
        const [assignmentsRes, unitsRes] = await Promise.all([
          httpClient(`${baseUrl}/user-assignments?page=0&size=1000&sort=id,asc`),
          httpClient(`${baseUrl}/units?page=0&size=1000&sort=id,asc`),
        ]);
        const assignments = assignmentsRes.json.content ?? [];
        const units = unitsRes.json.content ?? [];

        const userAssignments = assignments.filter((a: any) => a.userId === data.id && a.active);
        const unitNames = userAssignments
          .map((a: any) => {
            const unit = units.find((u: any) => u.id === a.unitId);
            return unit?.name ?? a.unitId;
          })
          .join(', ');

        data = {
          ...data,
          unitIds: userAssignments.map((a: any) => a.unitId),
          unitNames,
        };
      }

      if (resource === 'units') {
        const devicesRes = await httpClient(
          `${baseUrl}/devices?unitId=${params.id}&page=0&size=1000&sort=id,asc`
        );
        const devices = devicesRes.json.content ?? [];
        data = {
          ...data,
          catalogIds: devices.map((d: any) => d.catalogId),
        };
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
    if (resource === 'notifications') {
      if (params.id === 'read-all') {
        // Отметить все уведомления прочитанными
        const url = `${baseUrl}/${resource}/read-all`;
        return httpClient(url, { method: 'POST' }).then(() => ({ data: { id: 'read-all' } }));
      }
      // /admin/notifications/{id}/read — отметить уведомление прочитанным
      const url = `${baseUrl}/${resource}/${encodeURIComponent(params.id)}/read`;
      return httpClient(url, { method: 'POST' }).then(() => ({ data: { id: params.id } }));
    }

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
