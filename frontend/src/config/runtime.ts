import { EnvSchema } from '../schemas';

// Валидируем переменные окружения один раз при загрузке модуля.
// При некорректных значениях — console.error в dev, дальше работают дефолты.
// Это не throws: прод не должен упасть из-за отсутствующей opcional-переменной.
const _envResult = EnvSchema.safeParse(import.meta.env);
if (!_envResult.success && import.meta.env.DEV) {
  console.error(
    '[config] Некорректные переменные окружения:\n' +
      _envResult.error.issues.map((i) => `  ${String(i.path[0])}: ${i.message}`).join('\n')
  );
}

const runtimeOrigin = typeof window !== 'undefined' ? window.location.origin : '';
const runtimeWsOrigin =
  typeof window !== 'undefined'
    ? `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`
    : '';

// Дефолт через current-origin: это убирает зависимость от localhost на телефоне/ngrok.
export const API_BASE = import.meta.env.VITE_API_BASE ?? runtimeOrigin;
export const WS_BASE = import.meta.env.VITE_WS_BASE ?? runtimeWsOrigin;

export const WS_RECONNECT_BASE_DELAY_MS = 2_000;
export const WS_RECONNECT_MAX_DELAY_MS = 30_000;
export const WS_RECONNECT_JITTER = 0.25;
/**
 * Фиксированный интервал между попытками в режиме recovery
 * (после исчерпания {@link WS_ERROR_THRESHOLD_ATTEMPTS}).
 * Концептуально отличается от {@link WS_RECONNECT_MAX_DELAY_MS}:
 * это не ограничение backoff-а, а интервал опроса сервера.
 */
export const WS_RECOVERY_INTERVAL_MS = 30_000;
/**
 * Число последовательных неудачных попыток переподключения WS,
 * после которых считается, что соединение «потеряно»:
 * - в шапке появляется уведомление об ошибке;
 * - skeleton-заглушка заменяется текстовым сообщением об ошибке.
 *
 * До достижения порога соединение молча переподключается в фоне,
 * пользователь видит только skeleton.
 */
export const WS_ERROR_THRESHOLD_ATTEMPTS = 5;

export const ASYNC_FETCH_DEFAULT_RETRY_CONFIG = Object.freeze({
  maxAttempts: 4,
  baseDelayMs: 1_000,
  maxDelayMs: 30_000,
  factor: 2,
  recoveryDelayMs: 15_000,
});

export const ASYNC_FETCH_JITTER_CONFIG = Object.freeze({
  baseMultiplier: 0.75,
  rangeMultiplier: 0.25,
});

export const ALERT_VIBRATION_PATTERN = [200, 100, 200] as const;

export const HTTP_STATUS = Object.freeze({
  unauthorized: 401,
  forbidden: 403,
  notFound: 404,
  clientErrorMin: 400,
  serverErrorMin: 500,
});

export const UI_ANIMATION = Object.freeze({
  fadeInFast: 'fadeIn 0.2s ease',
  fadeInDefault: 'fadeIn 0.3s ease',
});

export const UI_BEHAVIOR = Object.freeze({
  emptyCollectionSize: 0,
  fabCollapseScrollDeltaPx: 4,
  fabSentResetDelayMs: 2_000,
  detailsBottomPaddingPx: 80,
  dashboardSkeletonCount: 3,
  workshopSkeletonCount: 4,
});
