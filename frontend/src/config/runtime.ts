export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';
export const WS_BASE = import.meta.env.VITE_WS_BASE ?? 'ws://localhost:8080';

export const WS_RECONNECT_BASE_DELAY_MS = 2_000;
export const WS_RECONNECT_MAX_DELAY_MS = 30_000;
export const WS_RECONNECT_JITTER = 0.25;

export const ASYNC_FETCH_DEFAULT_RETRY_CONFIG = Object.freeze({
  maxAttempts: 4,
  baseDelayMs: 1_000,
  maxDelayMs: 30_000,
  factor: 2,
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
