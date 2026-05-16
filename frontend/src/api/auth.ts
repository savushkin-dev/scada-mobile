import { API_BASE, HTTP_REQUEST } from '../config';
import { getRefreshToken, setTokens } from '../auth/session';

export interface LoginResponse {
  userId: string;
  accessToken: string;
  refreshToken: string;
}

interface LoginPayload {
  workerCode: string;
  password: string;
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
    throw new Error(`HTTP ${resp.status}`);
  }

  const raw: unknown = await resp.json();
  const rawObj = raw as {
    userId?: string | number;
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
 * При успехе сохраняет новую пару токенов.
 */
export async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  try {
    const resp = await fetch(`${API_BASE}/api/v1.0.0/auth/refresh`, {
      method: HTTP_REQUEST.post,
      headers: {
        'Content-Type': HTTP_REQUEST.jsonContentType,
      },
      body: JSON.stringify({ refreshToken }),
    });

    if (!resp.ok) return null;

    const raw: unknown = await resp.json();
    const rawObj = raw as {
      accessToken?: string;
      refreshToken?: string;
    } | null;

    if (!rawObj?.accessToken || !rawObj?.refreshToken) {
      return null;
    }

    setTokens(rawObj.accessToken, rawObj.refreshToken);
    return rawObj.accessToken;
  } catch {
    return null;
  }
}
