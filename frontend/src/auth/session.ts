const AUTH_STORAGE_KEY = 'scada.userId';
const ACCESS_TOKEN_KEY = 'scada.accessToken';
const REFRESH_TOKEN_KEY = 'scada.refreshToken';

function normalizeUserId(value: string): string {
  return value.trim();
}

export function getStoredUserId(): string | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    const normalized = normalizeUserId(raw);
    return normalized.length > 0 ? normalized : null;
  } catch {
    return null;
  }
}

export function getInitialUserId(): string | null {
  return getStoredUserId();
}

export function getAuthUserId(): string | null {
  return getStoredUserId();
}

export function setStoredUserId(userId: string): void {
  const normalized = normalizeUserId(userId);
  if (!normalized) return;
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, normalized);
  } catch {
    // ignore storage failures (private mode, quota, etc.)
  }
}

export function clearStoredUserId(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  } catch {
    // ignore storage failures (private mode, quota, etc.)
  }
}

// ── Access / Refresh Token helpers ─────────────────────────────────────────

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setTokens(accessToken: string, refreshToken: string): void {
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  } catch {
    // ignore storage failures
  }
}

export function setAccessToken(accessToken: string): void {
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  } catch {
    // ignore storage failures
  }
}

/**
 * Полная очистка всех auth-данных из хранилища.
 * Вызывается при logout.
 */
export function clearAllAuthData(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    localStorage.removeItem('scada.assignedUnits');
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    // ignore storage failures (private mode, quota, etc.)
  }
}
