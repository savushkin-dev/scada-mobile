/**
 * useAsyncFetch — хук с встроенной стратегией повторных попыток
 * (экспоненциальная задержка + jitter).
 *
 * Особенности:
 * - Каждая попытка прерывается по AbortSignal при размонтировании / смене deps.
 * - Параллельные запуски защищены счётчиком run-id — устаревшие результаты
 *   не попадают в состояние.
 * - 4xx ошибки считаются окончательными: ретрай не выполняется.
 * - Ретраи логируются в консоль; пользователь видит только состояние загрузки.
 * - refetch() принудительно перезапускает весь цикл.
 * - status: FetchStatus позволяет компонентам рендерить скелетоны вместо спиннеров.
 */

import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react';

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
  maxAttempts: 4,
  baseDelayMs: 1_000,
  maxDelayMs: 30_000,
  factor: 2,
};

// ── Утилиты ───────────────────────────────────────────────────────────
/** Задержка с разбросом ±25% для снижения thundering-herd. */
function computeDelay(attempt: number, cfg: RetryConfig): number {
  const exp = cfg.baseDelayMs * Math.pow(cfg.factor, attempt - 1);
  const capped = Math.min(exp, cfg.maxDelayMs);
  return Math.round(capped * (0.75 + Math.random() * 0.25));
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

/** Является ли HTTP-статус окончательной (не ретраябельной) ошибкой клиента. */
function isClientError(message: string): boolean {
  const match = /HTTP (\d{3})/.exec(message);
  if (!match) return false;
  const status = parseInt(match[1], 10);
  return status >= 400 && status < 500;
}

// ── Типы состояния ─────────────────────────────────────────────────────
export interface AsyncFetchState<T> {
  /** Удобный флаг для условий рендера: `true` пока status === 'loading'. */
  loading: boolean;
  /** Дискриминант — основа для скелетонов и явных веток в JSX. */
  status: FetchStatus;
  /** Данные после успешной загрузки; null в остальных состояниях. */
  data: T | null;
  /** Сообщение об ошибке после исчерпания всех попыток; null иначе. */
  error: string | null;
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
      let lastError = 'Неизвестная ошибка';

      for (let attempt = 1; attempt <= cfg.maxAttempts; attempt++) {
        if (stale || runIdRef.current !== runId) return;

        if (attempt > 1) {
          console.warn(`[useAsyncFetch] retry ${attempt}/${cfg.maxAttempts} after: ${lastError}`);
        }

        try {
          const data = await currentFn(controller.signal);

          if (stale || runIdRef.current !== runId) return;

          setState({ loading: false, status: 'success', data, error: null });
          return;
        } catch (e) {
          if ((e as Error).name === 'AbortError') return;

          lastError = e instanceof Error ? e.message : String(e);

          // Клиентские ошибки (4xx) — не ретраить.
          if (isClientError(lastError)) break;

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

      console.error(`[useAsyncFetch] all ${cfg.maxAttempts} attempts failed: ${lastError}`);
      setState({ loading: false, status: 'error', data: null, error: lastError });
    })();

    return markStale;
    // cfg.* включены явно, чтобы eslint-plugin-react-hooks не жаловался
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, refetchKey, cfg.maxAttempts, cfg.baseDelayMs, cfg.maxDelayMs, cfg.factor]);

  return { ...state, refetch };
}
