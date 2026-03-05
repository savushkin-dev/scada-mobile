export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';
export const WS_BASE = import.meta.env.VITE_WS_BASE ?? 'ws://localhost:8080';

// ── WebSocket reconnect ────────────────────────────────────────────────
/**
 * Начальная задержка перед первым переподключением, мс.
 * Используется как единственный источник правды для всех WS-хуков.
 */
export const WS_RECONNECT_BASE_DELAY_MS = 2_000;

/**
 * Верхний предел задержки переподключения, мс.
 */
export const WS_RECONNECT_MAX_DELAY_MS = 30_000;

/**
 * Коэффициент jitter: фактическая задержка умножается на `1 + Random(0, JITTER)`.
 * Снижает thundering-herd при одновременном реконнекте многих клиентов.
 */
export const WS_RECONNECT_JITTER = 0.25;
