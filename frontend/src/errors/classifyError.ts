/**
 * classifyError — единственный источник правды для классификации ошибок.
 *
 * ─ Аналог @ControllerAdvice + набора @ExceptionHandler в Spring ─────────────
 *
 * В Spring вы пишете:
 *
 *   @ControllerAdvice
 *   public class GlobalExceptionHandler {
 *     @ExceptionHandler(HttpClientErrorException.class)
 *     ResponseEntity<?> handleClientError(HttpClientErrorException e) { ... }
 *
 *     @ExceptionHandler(ResourceAccessException.class)
 *     ResponseEntity<?> handleNetworkError(ResourceAccessException e) { ... }
 *   }
 *
 * Здесь — ровно то же самое, но на TypeScript:
 * - classifyError() — единственная функция, принимающая решение.
 * - Каждая ветка `if` — это отдельный @ExceptionHandler.
 * - Все вызывающие (useAsyncFetch, ErrorBoundary) передают сырую ошибку
 *   и получают обратно типизированный AppError — без собственной логики.
 *
 * ВАЖНО: не добавляй логику интерпретации ошибок в компоненты или хуки.
 * Если нужно обработать новый тип ошибки — добавь ветку здесь.
 */

import { ERROR_MESSAGES, HTTP_STATUS } from '../config';
import type { AppError, AppErrorCode, AppErrorSeverity, AppErrorSource } from './AppError';
import { HttpError } from './AppError';

// Внутренний тип: всё, что не зависит от источника.
type ErrorResolution = Omit<AppError, 'source' | 'raw'>;

// ── Публичный API ─────────────────────────────────────────────────────

/**
 * Принимает любую пойманную ошибку и источник, возвращает AppError.
 *
 * @param raw     Пойманное значение из catch (error) — может быть чем угодно.
 * @param source  Откуда брошена ошибка — используется для source и логирования.
 */
export function classifyError(raw: unknown, source: AppErrorSource): AppError {
  const rawMessage = raw instanceof Error ? raw.message : String(raw);
  const resolution = resolveHandler(raw);
  return {
    ...resolution,
    source,
    raw: rawMessage,
  };
}

// ── Диспетчер обработчиков (@ControllerAdvice dispatch) ──────────────

/**
 * Выбирает нужный @ExceptionHandler по типу ошибки.
 * Порядок имеет значение: от специфичных типов — к общим.
 */
function resolveHandler(error: unknown): ErrorResolution {
  // ── @ExceptionHandler(HttpError.class) ───────────────────────────────
  // HttpError — наш явный тип из api-слоя. Несёт статус-код числом.
  if (error instanceof HttpError) {
    return handleHttpError(error.status);
  }

  if (!(error instanceof Error)) {
    // Пойман не-Error (строка, null, объект — крайне редко).
    return fallback();
  }

  // ── @ExceptionHandler(AbortError.class) ──────────────────────────────
  // AbortError от браузера при отмене fetch через AbortController.
  // В норме useAsyncFetch перехватывает его раньше; сюда попадает
  // только если signal.abort() произошёл вне нашего контроля.
  if (error.name === 'AbortError') {
    return {
      code: 'timeout',
      message: ERROR_MESSAGES.timeout,
      severity: 'transient',
      retryable: true,
    };
  }

  // ── @ExceptionHandler(NetworkError.class) ────────────────────────────
  // Браузер бросает TypeError при потере сети, CORS-ошибке или
  // недоступности сервера — сообщение варьируется по браузерам.
  if (
    error instanceof TypeError &&
    (error.message.toLowerCase().includes('fetch') ||
      error.message.toLowerCase().includes('network') ||
      error.message.toLowerCase().includes('failed to') ||
      error.message.toLowerCase().includes('load failed'))
  ) {
    return {
      code: 'network_unavailable',
      message: ERROR_MESSAGES.networkUnavailable,
      severity: 'degraded',
      retryable: true,
    };
  }

  // ── @ExceptionHandler(SyntaxError.class) ─────────────────────────────
  // JSON.parse() выбрасывает SyntaxError при некорректном теле ответа.
  if (error instanceof SyntaxError) {
    return {
      code: 'parse_error',
      message: ERROR_MESSAGES.parseError,
      severity: 'degraded',
      retryable: true,
    };
  }

  // ── @ExceptionHandler(RenderError.class) — из ErrorBoundary ──────────
  if (error.name === 'RenderError') {
    return {
      code: 'render_crash',
      message: ERROR_MESSAGES.renderCrash,
      severity: 'critical',
      retryable: true,
    };
  }

  // ── Fallback (@ExceptionHandler(Exception.class)) ────────────────────
  return fallback();
}

// ── Специализированные обработчики ───────────────────────────────────

/**
 * @ExceptionHandler(HttpError.class) — ветвление по HTTP-статусу.
 *
 * Каждый диапазон статусов — отдельный «кейс» обработчика:
 * - 401/403 → critical (нет доступа, повтор бессмысленен)
 * - 404     → degraded (ресурс не найден, повтор бессмысленен)
 * - 4xx     → degraded (ошибка клиента, повтор бессмысленен)
 * - 5xx     → degraded (ошибка сервера, повтор имеет смысл)
 */
function handleHttpError(status: number): ErrorResolution {
  if (status === HTTP_STATUS.unauthorized || status === HTTP_STATUS.forbidden) {
    return {
      code: 'client_error',
      message: ERROR_MESSAGES.accessDenied,
      severity: 'critical',
      retryable: false,
    };
  }
  if (status === HTTP_STATUS.notFound) {
    return {
      code: 'not_found',
      message: ERROR_MESSAGES.notFound,
      severity: 'degraded',
      retryable: false,
    };
  }
  if (status >= HTTP_STATUS.clientErrorMin && status < HTTP_STATUS.serverErrorMin) {
    return {
      code: 'client_error',
      message: ERROR_MESSAGES.requestError(status),
      severity: 'degraded',
      retryable: false,
    };
  }
  if (status >= HTTP_STATUS.serverErrorMin) {
    return {
      code: 'server_error',
      message: ERROR_MESSAGES.serverError,
      severity: 'degraded',
      retryable: true,
    };
  }
  // Неожиданный статус (1xx, 2xx с ошибкой, 3xx без редиректа)
  return {
    code: 'unknown' as AppErrorCode,
    message: ERROR_MESSAGES.unexpectedServerResponse(status),
    severity: 'degraded' as AppErrorSeverity,
    retryable: true,
  };
}

/** Глобальный @ExceptionHandler(Exception.class) — последний рубеж. */
function fallback(): ErrorResolution {
  return {
    code: 'unknown',
    message: ERROR_MESSAGES.unexpectedError,
    severity: 'degraded',
    retryable: true,
  };
}
