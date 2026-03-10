/**
 * AppError — унифицированный тип ошибки приложения.
 *
 * ─ Аналог ProblemDetail (RFC 7807) / ResponseEntityExceptionHandler в Spring ──
 * В Spring все ошибки контроллеров в конечном счёте приводятся к одному формату
 * ответа (ProblemDetail или ErrorResponse). Здесь — ровно то же самое:
 * независимо от источника (HTTP 500, потеря сети, crash рендера) до UI
 * доходит единая структура AppError с предсказуемым набором полей.
 *
 * Использование:
 * - classifyError.ts — единственное место, где создаются AppError-объекты.
 * - useAsyncFetch.ts — хранит AppError | null вместо string | null.
 * - HeaderErrorIndicator.tsx — рендерит активную ошибку в общей шапке.
 * - ErrorBoundary.tsx — фиксирует render-краши через classifyError.
 */

// ── Типы источника и тяжести ──────────────────────────────────────────

/**
 * Источник ошибки — откуда она возникла.
 *
 * Аналог специализации @ExceptionHandler(SomeException.class) в Spring:
 * каждый источник может получить разное поведение в classifyError.
 */
export type AppErrorSource =
  | 'topology/workshops' // GET /workshops/topology
  | 'topology/units' // GET /workshops/{id}/units/topology
  | 'ws/live' // WS  /ws/live (мультиплексный канал)
  | 'ws/unit' // WS  /ws/unit/{unitId}
  | 'ws/workshops-status' // WS  /ws/workshops/status (legacy)
  | 'ws/units-status' // WS  /ws/workshops/{id}/units/status (legacy)
  | 'ui/render' // Ошибка React-рендера (ErrorBoundary)
  | 'unknown'; // Источник не определён

/**
 * Тяжесть ошибки.
 * - `critical`  — приложение неработоспособно (нет доступа, fatal crash).
 * - `degraded`  — часть данных недоступна, остальное работает.
 * - `transient` — временная проблема, идут авто-ретраи.
 */
export type AppErrorSeverity = 'critical' | 'degraded' | 'transient';

/**
 * Код ошибки — машиночитаемый идентификатор типа.
 *
 * Аналог Spring ErrorCode / ProblemDetail.type (URI в RFC 7807):
 * компоненты могут ветвиться по code, не разбирая сырые строки.
 */
export type AppErrorCode =
  | 'network_unavailable' // Failed to fetch / NetworkError
  | 'server_error' // 5xx
  | 'not_found' // 404
  | 'client_error' // другие 4xx
  | 'timeout' // AbortError не от нашего сигнала
  | 'parse_error' // SyntaxError при разборе JSON
  | 'render_crash' // React ErrorBoundary
  | 'unknown'; // Всё остальное

// ── Основной тип ───────────────────────────────────────────────────────

/**
 * Типизированная ошибка приложения.
 *
 * Создаётся исключительно через classifyError() — не конструируй напрямую.
 */
export interface AppError {
  /** Машиночитаемый идентификатор типа ошибки. */
  code: AppErrorCode;
  /** Человекочитаемое сообщение для пользователя (на русском). */
  message: string;
  /** Откуда пришла ошибка. */
  source: AppErrorSource;
  /** Тяжесть — определяет визуальное оформление и приоритет. */
  severity: AppErrorSeverity;
  /** Имеет ли смысл показывать кнопку «Повторить». */
  retryable: boolean;
  /** Оригинальное техническое сообщение — только для console.error / логов. */
  raw: string;
}

// ── Специализированные классы исключений ─────────────────────────────

/**
 * Ошибка HTTP-запроса с явным статус-кодом.
 *
 * Аналог Spring HttpClientErrorException / HttpServerErrorException —
 * несёт конкретный HTTP-статус, что позволяет classifyError
 * обрабатывать его типобезопасно, без парсинга строк вроде /HTTP (\d{3})/.
 *
 * Бросается исключительно в api-слое (api/workshops.ts и аналогах).
 */
export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string = `HTTP ${status}`
  ) {
    super(message);
    this.name = 'HttpError';
  }
}
