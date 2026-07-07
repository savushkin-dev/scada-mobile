import { HTTP_REQUEST } from '../config';
import { getAccessToken, clearAllAuthData } from '../auth/session';
import { refreshAccessToken } from './auth';
import { AuthSessionExpiredError } from '../errors/AppError';

let isRefreshing = false;
let refreshSubscribers: Array<(token: string | null) => void> = [];

function subscribeToRefresh(callback: (token: string | null) => void): void {
  refreshSubscribers.push(callback);
}

function notifySubscribers(token: string | null): void {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

/**
 * Логирует HTTP-запрос/ответ в dev-режиме.
 * Формат: → METHOD url  ← STATUS url
 */
function logRequest(method: string, url: string, status?: number): void {
  if (!import.meta.env.DEV) return;
  const label = status === undefined ? '→' : '←';
  const statusStr = status !== undefined ? ` — ${status}` : '';

  console.log(`[api] ${label} ${method} ${url}${statusStr}`);
}

/**
 * Централизованный fetch с автоматическим:
 * - Добавлением Authorization: Bearer <accessToken>
 * - Обработкой 401 → попытка refresh → retry запроса
 * - Если refresh не удался — выбрасывает AuthSessionExpiredError
 */
export async function apiFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers(options.headers);
  const method = options.method?.toUpperCase() ?? 'GET';

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  // Ensure Content-Type is set for JSON bodies
  if (options.body && typeof options.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', HTTP_REQUEST.jsonContentType);
  }

  logRequest(method, url);

  const resp = await fetch(url, { ...options, headers });

  logRequest(method, url, resp.status);

  // Если не 401 — возвращаем как есть
  if (resp.status !== 401) {
    return resp;
  }

  // 401 — пробуем refresh
  if (isRefreshing) {
    // Ждём завершения текущего refresh
    return new Promise((resolve, reject) => {
      subscribeToRefresh((newToken) => {
        if (!newToken) {
          reject(new AuthSessionExpiredError());
          return;
        }
        const retryHeaders = new Headers(options.headers);
        retryHeaders.set('Authorization', `Bearer ${newToken}`);
        resolve(fetch(url, { ...options, headers: retryHeaders }));
      });
    });
  }

  isRefreshing = true;
  let newToken: string | null = null;
  let refreshError: unknown = null;

  try {
    newToken = await refreshAccessToken();
  } catch (e) {
    refreshError = e;
  }

  isRefreshing = false;
  notifySubscribers(newToken);

  if (!newToken) {
    // Refresh не удался. Различаем причину:
    // - Сетевая ошибка → сервер недоступен, токены валидны, не выгоняем пользователя
    // - HTTP-ошибка (4xx/5xx) → токены протухли/инвалидированы → logout
    if (refreshError && isNetworkError(refreshError)) {
      // Сервер недоступен — не чистим auth, прокидываем сетевую ошибку
      throw refreshError;
    }
    // Refresh не удался по HTTP-причине — токены протухли или инвалидированы
    clearAllAuthData();
    throw new AuthSessionExpiredError();
  }

  // Retry с новым токеном
  const retryHeaders = new Headers(options.headers);
  retryHeaders.set('Authorization', `Bearer ${newToken}`);
  return fetch(url, { ...options, headers: retryHeaders });
}

/**
 * Обёртка над apiFetch с автоматическим JSON.parse.
 * Бросает HttpError при !resp.ok.
 */
export async function apiFetchJson(url: string, options?: RequestInit): Promise<unknown> {
  const resp = await apiFetch(url, options);
  if (!resp.ok) {
    const error = new Error(`HTTP ${resp.status}`);
    error.name = 'HttpError';
    (error as unknown as { status: number }).status = resp.status;
    throw error;
  }
  return resp.json();
}
