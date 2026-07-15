import { API_BASE, HTTP_REQUEST } from '../config';
import { getRefreshToken, setTokens } from '../auth/session';
import { apiFetch } from './client';
import {
  AuthSessionExpiredError,
  NetworkUnavailableError,
  ServerUnavailableError,
} from '../errors/AppError';

export interface LoginResponse {
  userId: string;
  role: string;
  temporaryPassword: boolean;
  accessToken: string;
  refreshToken: string;
}

interface LoginPayload {
  workerCode: string;
  password: string;
}

interface ChangePasswordPayload {
  newPassword: string;
}

export async function loginUser(payload: LoginPayload): Promise<LoginResponse> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/auth/login`, {
    method: HTTP_REQUEST.post,
    headers: {
      'Content-Type': HTTP_REQUEST.jsonContentType,
    },
    body: JSON.stringify(payload),
  });

  if (!resp.ok) {
    const errorBody = (await resp.json().catch(() => null)) as { message?: string } | null;
    const message = errorBody?.message ?? `HTTP ${resp.status}`;
    throw new Error(`${resp.status}|${message}`);
  }

  const raw: unknown = await resp.json();
  const rawObj = raw as {
    userId?: string | number;
    role?: string;
    temporaryPassword?: boolean;
    accessToken?: string;
    refreshToken?: string;
  } | null;

  const candidate = rawObj?.userId != null ? String(rawObj.userId).trim() : '';
  if (!candidate) {
    throw new Error('Missing userId in auth response');
  }
  if (!rawObj?.accessToken || !rawObj?.refreshToken) {
    throw new Error('Missing tokens in auth response');
  }

  return {
    userId: candidate,
    role: rawObj.role ?? '',
    temporaryPassword: rawObj.temporaryPassword ?? false,
    accessToken: rawObj.accessToken,
    refreshToken: rawObj.refreshToken,
  };
}

/**
 * Запрос на смену пароля текущего пользователя.
 * Требует валидный access-токен. После успешной смены возвращает новую пару токенов.
 */
export async function changePassword(payload: ChangePasswordPayload): Promise<LoginResponse> {
  const resp = await apiFetch(`${API_BASE}/api/v1.0.0/auth/change-password`, {
    method: HTTP_REQUEST.post,
    headers: {
      'Content-Type': HTTP_REQUEST.jsonContentType,
    },
    body: JSON.stringify(payload),
  });

  if (!resp.ok) {
    const errorBody = (await resp.json().catch(() => null)) as { message?: string } | null;
    const message = errorBody?.message ?? `HTTP ${resp.status}`;
    throw new Error(`${resp.status}|${message}`);
  }

  const raw: unknown = await resp.json();
  const rawObj = raw as {
    userId?: string | number;
    role?: string;
    temporaryPassword?: boolean;
    accessToken?: string;
    refreshToken?: string;
  } | null;

  const candidate = rawObj?.userId != null ? String(rawObj.userId).trim() : '';
  if (!candidate || !rawObj?.accessToken || !rawObj?.refreshToken) {
    throw new Error('Invalid response from change-password endpoint');
  }

  setTokens(rawObj.accessToken, rawObj.refreshToken);

  return {
    userId: candidate,
    role: rawObj.role ?? '',
    temporaryPassword: rawObj.temporaryPassword ?? false,
    accessToken: rawObj.accessToken,
    refreshToken: rawObj.refreshToken,
  };
}

/**
 * Запрос на выход из аккаунта.
 * Отправляет refresh-токен для инвалидации на сервере.
 */
export async function logoutUser(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    await fetch(`${API_BASE}/api/v1.0.0/auth/logout`, {
      method: HTTP_REQUEST.post,
      headers: {
        'Content-Type': HTTP_REQUEST.jsonContentType,
      },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    // Игнорируем сетевые ошибки — logout должен работать локально
  }
}

/**
 * Обновление access-токена через refresh-токен.
 *
 * Возможные исходы:
 * - Успех → возвращает новый accessToken, сохраняет пару токенов.
 * - Сетевая ошибка → бросает NetworkUnavailableError (не трогаем токены).
 * - 401/403 или некорректный ответ → бросает AuthSessionExpiredError (logout).
 * - 5xx → бросает ServerUnavailableError (не трогаем токены).
 */
export async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new AuthSessionExpiredError('Отсутствует refresh-токен');
  }

  let resp: Response;
  try {
    resp = await fetch(`${API_BASE}/api/v1.0.0/auth/refresh`, {
      method: HTTP_REQUEST.post,
      headers: {
        'Content-Type': HTTP_REQUEST.jsonContentType,
      },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    // Fetch бросает TypeError при сетевых сбоях (сервер недоступен, CORS, разрыв).
    throw new NetworkUnavailableError();
  }

  if (!resp.ok) {
    if (resp.status === 401 || resp.status === 403) {
      throw new AuthSessionExpiredError(`HTTP ${resp.status}`);
    }
    if (resp.status >= 500) {
      throw new ServerUnavailableError(resp.status);
    }
    // Другие 4xx — тоже считаем истечением сессии, т.к. refresh не должен так отвечать
    throw new AuthSessionExpiredError(`HTTP ${resp.status}`);
  }

  const raw: unknown = await resp.json();
  const rawObj = raw as {
    accessToken?: string;
    refreshToken?: string;
  } | null;

  if (!rawObj?.accessToken || !rawObj?.refreshToken) {
    throw new AuthSessionExpiredError('Некорректный ответ при обновлении токена');
  }

  setTokens(rawObj.accessToken, rawObj.refreshToken);
  return rawObj.accessToken;
}
