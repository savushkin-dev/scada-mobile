const AUTH_STORAGE_KEY = 'scada.userId';
const ROLE_STORAGE_KEY = 'scada.role';
const ACCESS_TOKEN_KEY = 'scada.accessToken';
const REFRESH_TOKEN_KEY = 'scada.refreshToken';

/** Канал для синхронизации auth-состояния между вкладками. */
const AUTH_BROADCAST_CHANNEL = 'scada-auth-sync';

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
    broadcastAuthEvent({ type: 'tokens-updated' });
  } catch {
    // ignore storage failures
  }
}

export function setAccessToken(accessToken: string): void {
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    broadcastAuthEvent({ type: 'tokens-updated' });
  } catch {
    // ignore storage failures
  }
}

export function getStoredRole(): string | null {
  try {
    return localStorage.getItem(ROLE_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setStoredRole(role: string): void {
  try {
    localStorage.setItem(ROLE_STORAGE_KEY, role);
  } catch {
    // ignore
  }
}

export function clearStoredRole(): void {
  try {
    localStorage.removeItem(ROLE_STORAGE_KEY);
  } catch {
    // ignore
  }
}

/**
 * Полная очистка всех auth-данных из хранилища.
 * Вызывается при logout.
 */
export function clearAllAuthData(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    localStorage.removeItem(ROLE_STORAGE_KEY);
    localStorage.removeItem('scada.assignedUnits');
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    broadcastAuthEvent({ type: 'logout' });
  } catch {
    // ignore storage failures (private mode, quota, etc.)
  }
}

// ── Multi-tab synchronization ────────────────────────────────────────────

interface AuthBroadcastEvent {
  type: 'tokens-updated' | 'logout';
}

function broadcastAuthEvent(event: AuthBroadcastEvent): void {
  try {
    const bc = new BroadcastChannel(AUTH_BROADCAST_CHANNEL);
    bc.postMessage(event);
    bc.close();
  } catch {
    // BroadcastChannel не поддерживается — fallback через storage event
    try {
      localStorage.setItem('scada.auth-event', JSON.stringify({ ...event, ts: Date.now() }));
      localStorage.removeItem('scada.auth-event');
    } catch {
      // ignore
    }
  }
}

/**
 * Подписывается на события синхронизации auth между вкладками.
 * @param onLogout callback при logout в другой вкладке
 * @param onTokensUpdated callback при обновлении токенов в другой вкладке
 * @returns функция отписки
 */
export function subscribeToAuthSync(onLogout: () => void, onTokensUpdated: () => void): () => void {
  let bc: BroadcastChannel | null = null;

  const handleMessage = (event: MessageEvent<AuthBroadcastEvent>) => {
    if (event.data?.type === 'logout') {
      onLogout();
    } else if (event.data?.type === 'tokens-updated') {
      onTokensUpdated();
    }
  };

  const handleStorage = (event: StorageEvent) => {
    if (event.key === 'scada.auth-event' && event.newValue) {
      try {
        const data = JSON.parse(event.newValue) as AuthBroadcastEvent;
        if (data.type === 'logout') {
          onLogout();
        } else if (data.type === 'tokens-updated') {
          onTokensUpdated();
        }
      } catch {
        // ignore parse errors
      }
    }
  };

  try {
    bc = new BroadcastChannel(AUTH_BROADCAST_CHANNEL);
    bc.addEventListener('message', handleMessage);
  } catch {
    // BroadcastChannel не поддерживается — используем storage event
  }

  window.addEventListener('storage', handleStorage);

  return () => {
    if (bc) {
      bc.removeEventListener('message', handleMessage);
      bc.close();
    }
    window.removeEventListener('storage', handleStorage);
  };
}
