/**
 * SCADA Mobile — API-слой.
 *
 * Единственное место, где знают о backend URL и структуре ответов.
 * Вся связь с бекендом идёт через функции этого модуля.
 */

// ─── Конфигурация ────────────────────────────────────────────────────────────

const DEFAULT_HOST = window.location.hostname || "localhost";
const BASE_URL = window.SCADA_API_BASE_URL ?? `${window.location.protocol}//${DEFAULT_HOST}:8080`;

const ENDPOINTS = {
  QUERY_ALL:    `${BASE_URL}/api/v1/commands/queryAll`,
  SET_UNIT_VARS: `${BASE_URL}/api/v1/commands/setUnitVars`,
  HEALTH_READY: `${BASE_URL}/api/v1/commands/health/ready`,
};

// ─── Типы (JSDoc для IDE) ────────────────────────────────────────────────────

/**
 * @typedef {Object} UnitProperties
 * @property {number|null} command
 * @property {string|null} message
 * @property {string|null} Error
 * @property {string|null} ErrorMessage
 */

/**
 * @typedef {Object} UnitState
 * @property {string|null} State
 * @property {string|null} Task
 * @property {number|null} Counter
 * @property {UnitProperties|null} Properties
 */

/**
 * @typedef {Object} QueryStateResponse
 * @property {string} DeviceName
 * @property {Object.<string, UnitState>} Units
 */

/**
 * @typedef {Object} ApiError
 * @property {number}  status
 * @property {string}  message
 * @property {string}  timestamp
 * @property {string}  path
 */

// ─── Вспомогательные ────────────────────────────────────────────────────────

/**
 * Базовый fetch с обработкой non-2xx и JSON-ошибок бека.
 * @param {string} url
 * @param {RequestInit} [options]
 * @returns {Promise<unknown>}
 */
async function apiFetch(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);

  const headers = new Headers(options.headers ?? {});
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }

  const hasBody = options.body != null;
  if (hasBody && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      /** @type {ApiError} */
      let errorBody;
      try {
        errorBody = await response.json();
      } catch {
        errorBody = { status: response.status, message: response.statusText };
      }
      throw new ApiException(
        errorBody.message ?? `HTTP ${response.status}`,
        response.status,
        errorBody
      );
    }

    return await response.json();
  } catch (err) {
    clearTimeout(timeoutId);
    if (err instanceof ApiException) throw err;
    if (err.name === "AbortError") {
      throw new ApiException("Сервер не ответил вовремя (таймаут)", 0);
    }
    throw new ApiException(
      "Нет связи с сервером. Проверьте, что бекенд запущен.",
      0
    );
  }
}

// ─── Класс ошибки ────────────────────────────────────────────────────────────

export class ApiException extends Error {
  /**
   * @param {string}      message
   * @param {number}      statusCode
   * @param {ApiError}   [body]
   */
  constructor(message, statusCode, body = null) {
    super(message);
    this.name = "ApiException";
    this.statusCode = statusCode;
    this.body = body;
  }
}

// ─── Публичные функции API ───────────────────────────────────────────────────

/**
 * Получить текущий snapshot состояния SCADA.
 * GET /api/v1/commands/queryAll
 * @returns {Promise<QueryStateResponse>}
 */
export async function fetchSnapshot() {
  return /** @type {QueryStateResponse} */ (
    await apiFetch(ENDPOINTS.QUERY_ALL)
  );
}

/**
 * Отправить команду SetUnitVars для конкретного unit.
 * POST /api/v1/commands/setUnitVars?unit={unit}&value={value}
 *
 * @param {number} unit  — номер unit, 1-based (1 = u1, 2 = u2, …)
 * @param {number} value — новое значение команды (>= 1)
 * @returns {Promise<unknown>} — acknowledgment (NOT реальное состояние)
 */
export async function sendSetUnitVars(unit, value) {
  if (!Number.isInteger(unit) || unit < 1) {
    throw new ApiException("unit должен быть целым числом >= 1", 0);
  }
  if (!Number.isInteger(value) || value < 1) {
    throw new ApiException("value должен быть целым числом >= 1", 0);
  }

  const url = `${ENDPOINTS.SET_UNIT_VARS}?unit=${unit}&value=${value}`;
  return apiFetch(url, { method: "POST" });
}

/**
 * Проверить готовность бекенда (readiness probe).
 * GET /api/v1/commands/health/ready
 * @returns {Promise<boolean>} true если бекенд готов
 */
export async function checkReady() {
  try {
    const data = await apiFetch(ENDPOINTS.HEALTH_READY);
    return data?.ready === true;
  } catch {
    return false;
  }
}
