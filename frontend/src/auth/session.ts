import { USER_ID } from '../config';

const AUTH_STORAGE_KEY = 'scada.userId';

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
  return getAuthUserId();
}

export function getAuthUserId(): string | null {
  const stored = getStoredUserId();
  if (stored) return stored;
  if (!USER_ID) return null;
  const normalized = normalizeUserId(USER_ID);
  return normalized.length > 0 ? normalized : null;
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
