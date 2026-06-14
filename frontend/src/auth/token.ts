/**
 * Утилиты для работы с JWT-токенами на клиенте.
 *
 * Парсит payload без валидации подписи (это задача сервера).
 * Используется только для proactive-проверки срока действия.
 */

const TOKEN_EXPIRY_MARGIN_SECONDS = 60; // 1 минута запаса

interface JwtPayload {
  exp?: number;
  sub?: string;
  role?: string;
}

/**
 * Декодирует JWT payload из base64url.
 */
function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = atob(padded);
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

/**
 * Возвращает true, если токен истёк или истекает в ближайшие 1 минуту.
 */
export function isTokenExpired(token: string | null): boolean {
  if (!token) return true;
  const payload = decodeJwtPayload(token);
  if (!payload?.exp) return true;

  const expiryWithMargin = payload.exp - TOKEN_EXPIRY_MARGIN_SECONDS;
  return Math.floor(Date.now() / 1000) >= expiryWithMargin;
}

/**
 * Возвращает true, если токен уже полностью истёк (без margin).
 */
export function isTokenFullyExpired(token: string | null): boolean {
  if (!token) return true;
  const payload = decodeJwtPayload(token);
  if (!payload?.exp) return true;

  return Math.floor(Date.now() / 1000) >= payload.exp;
}

/**
 * Возвращает дату истечения токена или null.
 */
export function getTokenExpiryDate(token: string | null): Date | null {
  if (!token) return null;
  const payload = decodeJwtPayload(token);
  if (!payload?.exp) return null;
  return new Date(payload.exp * 1000);
}

/**
 * Возвращает число секунд до истечения токена (с margin).
 * Отрицательное значение = токен уже истёк.
 */
export function getTokenTimeRemaining(token: string | null): number {
  if (!token) return -1;
  const payload = decodeJwtPayload(token);
  if (!payload?.exp) return -1;

  const expiryWithMargin = payload.exp - TOKEN_EXPIRY_MARGIN_SECONDS;
  return expiryWithMargin - Math.floor(Date.now() / 1000);
}
