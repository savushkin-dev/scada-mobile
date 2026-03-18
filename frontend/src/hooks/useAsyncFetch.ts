/**
 * useAsyncFetch — хук с встроенной стратегией повторных попыток
 * (экспоненциальная задержка + jitter).
 *
 * Особенности:
 * - Каждая попытка прерывается по AbortSignal при размонтировании / смене deps.
 * - Параллельные запуски защищены счётчиком run-id — устаревшие результаты
 *   не попадают в состояние.
 * - 4xx ошибки считаются окончательными: ретрай не выполняется.
 * - После исчерпания обычных ретраев retryable-ошибки остаются видимыми в UI,
 *   а хук продолжает фоновые попытки восстановления, пока ресурс не оживёт.
 * - Ретраи логируются в консоль; пользователь видит только состояние загрузки.
 * - refetch() принудительно перезапускает весь цикл.
 * - status: FetchStatus позволяет компонентам рендерить скелетоны вместо спиннеров.
 */

import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react';
import {
  ASYNC_FETCH_DEFAULT_RETRY_CONFIG,
  ASYNC_FETCH_JITTER_CONFIG,
  ERROR_MESSAGES,
} from '../config';
import { classifyError } from '../errors/classifyError';
import type { AppError, AppErrorSource } from '../errors/AppError';

// ── Конфигурация ───────────────────────────────────────────────────────
export interface RetryConfig {
  /** Максимальное число попыток (включая первую). По умолчанию 4. */
  maxAttempts: number;
  /** Базовая задержка между попытками, мс. По умолчанию 1 000. */
  baseDelayMs: number;
  /** Верхний предел задержки, мс. По умолчанию 30 000. */
  maxDelayMs: number;
  /** Множитель для экспоненциального роста. По умолчанию 2. */
  factor: number;
  /**
   * Задержка между фоновыми циклами восстановления после исчерпания ретраев.
   * Пока backend недоступен, хук остаётся в состоянии error и периодически
   * выполняет новый полный retry-cycle без перезагрузки страницы.
   */
  recoveryDelayMs: number;
  /**
   * Источник запроса — передаётся в classifyError для точной классификации.
   * Аналог указания конкретного @ExceptionHandler в Spring —
   * позволяет classifyError вернуть разные сообщения для topology/workshops
   * вс topology/units если нужно.
   */
  source?: AppErrorSource;
}

/**
 * Дискриминант состояния загрузки.
 * Используй `status` вместо `loading && !error` в компонентах —
 * это делает логику ветвления явной и готовит компоненты к скелетонам.
 *
 * - `'idle'`    — загрузка ещё не запускалась (fn === null).
 * - `'loading'` — первая попытка или ретраи; пользователю показывать скелетон.
 * - `'success'` — данные получены.
 * - `'error'`   — все попытки исчерпаны; данных нет.
 */
export type FetchStatus = 'idle' | 'loading' | 'error' | 'success';

const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxAttempts: ASYNC_FETCH_DEFAULT_RETRY_CONFIG.maxAttempts,
  baseDelayMs: ASYNC_FETCH_DEFAULT_RETRY_CONFIG.baseDelayMs,
  maxDelayMs: ASYNC_FETCH_DEFAULT_RETRY_CONFIG.maxDelayMs,
  factor: ASYNC_FETCH_DEFAULT_RETRY_CONFIG.factor,
  recoveryDelayMs: ASYNC_FETCH_DEFAULT_RETRY_CONFIG.recoveryDelayMs,
};

// ── Утилиты ───────────────────────────────────────────────────────────
/** Задержка с разбросом ±25% для снижения thundering-herd. */
function computeDelay(attempt: number, cfg: RetryConfig): number {
  const exp = cfg.baseDelayMs * Math.pow(cfg.factor, attempt - 1);
  const capped = Math.min(exp, cfg.maxDelayMs);
  return Math.round(
    capped *
      (ASYNC_FETCH_JITTER_CONFIG.baseMultiplier +
        Math.random() * ASYNC_FETCH_JITTER_CONFIG.rangeMultiplier)
  );
}

/** Promise, который разрешается через `ms` мс или отменяется по сигналу. */
function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const id = setTimeout(resolve, ms);
    signal.addEventListener(
      'abort',
      () => {
        clearTimeout(id);
        reject(new DOMException('Aborted', 'AbortError'));
      },
      { once: true }
    );
  });
}

// ── Типы состояния ───────────────────────────────────────────────────
export interface AsyncFetchState<T> {
  /** Удобный флаг для условий рендера: `true` пока status === 'loading'. */
  loading: boolean;
  /** Дискриминант — основа для скелетонов и явных веток в JSX. */
  status: FetchStatus;
  /** Данные после успешной загрузки; null в остальных состояниях. */
  data: T | null;
  /**
   * Типизированная ошибка после исчерпания всех попыток; null иначе.
   * Содержит человекочитаемое `message`, `code`, `retryable` и др. поля —
   * компонентам не нужно интерпретировать строки самостоятельно.
   */
  error: AppError | null;
}

export interface UseAsyncFetchResult<T> extends AsyncFetchState<T> {
  /** Принудительно перезапустить загрузку с первой попытки. */
  refetch: () => void;
}

// ── Хук ───────────────────────────────────────────────────────────────
/**
 * @param fn     Асинхронная функция-загрузчик. Принимает AbortSignal.
 *               Передайте `null`, чтобы временно отключить загрузку.
 * @param deps   Зависимости, аналогично useEffect.
 * @param config Параметры retry-стратегии (опционально).
 */
export function useAsyncFetch<T>(
  fn: ((signal: AbortSignal) => Promise<T>) | null,
  deps: DependencyList,
  config?: Partial<RetryConfig>
): UseAsyncFetchResult<T> {
  const cfg = { ...DEFAULT_RETRY_CONFIG, ...config };

  const [state, setState] = useState<AsyncFetchState<T>>({
    loading: fn !== null,
    status: fn !== null ? 'loading' : 'idle',
    data: null,
    error: null,
  });

  // Внешний счётчик запусков: если запустился новый run — устаревший молчит.
  const runIdRef = useRef(0);

  // refetch увеличивает этот счётчик → useEffect видит новую dep и перезапускается.
  const [refetchKey, setRefetchKey] = useState(0);
  const refetch = useCallback(() => setRefetchKey((k) => k + 1), []);

  // Храним fn в ref, чтобы не пересоздавать эффект при каждом рендере.
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    const currentFn = fnRef.current;

    if (currentFn === null) {
      setState({ loading: false, status: 'idle', data: null, error: null });
      return;
    }

    const runId = ++runIdRef.current;
    const controller = new AbortController();
    let stale = false;

    const markStale = () => {
      stale = true;
      controller.abort();
    };

    // Сбрасываем в loading — пользователь видит скелетон/индикатор на всё время,
    // включая ожидание между ретраями. Детали попыток только в консоли.
    setState({ loading: true, status: 'loading', data: null, error: null });

    (async () => {
      while (!stale && runIdRef.current === runId) {
        // lastError: храним AppError вместо строки — передаётся в setState после исчерпания попыток.
        let lastError: AppError | null = null;

        for (let attempt = 1; attempt <= cfg.maxAttempts; attempt++) {
          if (stale || runIdRef.current !== runId) return;

          if (attempt > 1 && lastError) {
            // При ретраях: в лог идёт техническое сообщение (raw), пользователь видит только скелетон.
            console.warn(
              `[useAsyncFetch] retry ${attempt}/${cfg.maxAttempts} after: ${lastError.raw}`
            );
          }

          try {
            const data = await currentFn(controller.signal);

            if (stale || runIdRef.current !== runId) return;

            setState({ loading: false, status: 'success', data, error: null });
            return;
          } catch (e) {
            if ((e as Error).name === 'AbortError') return;

            // Классифицируем через единый классификатор: retryable определяет,
            // есть ли смысл продолжать ретраи для текущей ошибки.
            const classified = classifyError(e, cfg.source ?? 'unknown');
            lastError = classified;

            if (!classified.retryable) break;

            if (attempt < cfg.maxAttempts) {
              const delay = computeDelay(attempt, cfg);
              console.warn(
                `[useAsyncFetch] waiting ${delay}ms before retry ${attempt + 1}/${cfg.maxAttempts}`
              );
              try {
                await sleep(delay, controller.signal);
              } catch {
                return; // abort во время ожидания
              }
            }
          }
        }

        if (stale || runIdRef.current !== runId) return;

        const finalError =
          lastError ??
          classifyError(new Error(ERROR_MESSAGES.unknownError), cfg.source ?? 'unknown');
        console.error(`[useAsyncFetch] all ${cfg.maxAttempts} attempts failed: ${finalError.raw}`);
        setState({ loading: false, status: 'error', data: null, error: finalError });

        if (!finalError.retryable) return;

        console.warn(
          `[useAsyncFetch] scheduling recovery cycle in ${cfg.recoveryDelayMs}ms after failure: ${finalError.raw}`
        );
        try {
          await sleep(cfg.recoveryDelayMs, controller.signal);
        } catch {
          return;
        }
      }
    })();

    return markStale;
    // cfg.* включены явно; spread ...deps не нарушает корректность — deps стабилен снаружи
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    ...deps, // eslint-disable-line react-hooks/exhaustive-deps
    refetchKey,
    cfg.maxAttempts,
    cfg.baseDelayMs,
    cfg.maxDelayMs,
    cfg.factor,
    cfg.recoveryDelayMs,
  ]);

  return { ...state, refetch };
}
