/**
 * createManagedWs — фабрика управляемых WebSocket-соединений.
 *
 * ─ Аналог ConnectionFactory + RetryTemplate из Spring Integration ────────────
 * Вместо того чтобы каждый WS-хук знал, как переподключаться, — вся логика
 * живёт здесь и вызывается единообразно. Хуки передают только URL и коллбэки.
 *
 * Что гарантирует фабрика:
 * - Exponential backoff с jitter при каждом разрыве соединения (thundering-herd mitigation).
 * - Корректный teardown: все обработчики обнуляются ДО вызова close(),
 *   чтобы onclose не запустил reconnect после размонтирования.
 * - Проверка readyState перед close() — нет лишних консольных предупреждений.
 * - Параметры задержки берутся из config/index.ts (единственный источник правды).
 *
 * Использование:
 *   const conn = createManagedWs({ url, onMessage });
 *   conn.send('{"action":"PING"}');  // отправка; noop если закрыто
 *   conn.destroy();                  // cleanup при размонтировании
 */

import {
  WS_RECONNECT_BASE_DELAY_MS,
  WS_RECONNECT_MAX_DELAY_MS,
  WS_RECONNECT_JITTER,
  WS_RECOVERY_INTERVAL_MS,
  WS_ERROR_THRESHOLD_ATTEMPTS,
} from '../config';
import { classifyError } from '../errors/classifyError';
import type { AppError, AppErrorSource } from '../errors/AppError';

// ── Публичные типы ────────────────────────────────────────────────────

export interface ManagedWsOptions {
  /**
   * URL для подключения.
   * Может быть строкой или функцией, возвращающей строку.
   * Функция вызывается перед каждой попыткой подключения —
   * это позволяет использовать актуальный токен при реконнекте.
   */
  url: string | (() => string);
  /** Источник ошибки для общей классификации. */
  source?: AppErrorSource;
  /**
   * Вызывается при каждом успешном открытии соединения (включая реконнекты).
   * Идеальное место для восстановления подписок после обрыва.
   * Принимает свежий WebSocket-объект — можно сразу вызвать ws.send().
   */
  onOpen?: (ws: WebSocket) => void;
  /** Вызывается при каждом входящем сообщении. */
  onMessage: (event: MessageEvent) => void;
  /**
   * Вызывается при ПЕРВОМ разрыве соединения (начало серии переподключений).
   * Сигнализирует UI: «идут фоновые попытки» — показывай skeleton, но не ошибку.
   */
  onReconnecting?: () => void;
  /**
   * Вызывается однократно после {@link errorThresholdAttempts} последовательных
   * неудачных попыток. Именно здесь нужно показывать ошибку пользователю.
   */
  onError?: (error: AppError) => void;
  /**
   * Число последовательных неудач до вызова {@link onError}.
   * По умолчанию — {@link WS_ERROR_THRESHOLD_ATTEMPTS} (5).
   */
  errorThresholdAttempts?: number;
  /** Вызывается при восстановлении соединения (физически открыт WebSocket). */
  onRecovered?: () => void;
}

export interface ManagedWsConnection {
  /**
   * Отправляет данные, если соединение в состоянии OPEN.
   * Безопасно вызывать в любом состоянии — при неактивном соединении noop.
   */
  send: (data: string) => void;
  /**
   * Уничтожает соединение и отменяет все ожидающие таймеры reconnect.
   * После вызова объект больше не пригоден для использования.
   */
  destroy: () => void;
}

// ── Фабрика ───────────────────────────────────────────────────────────

/**
 * Создаёт управляемое WebSocket-соединение с автоматическим reconnect.
 *
 * @param options Параметры соединения и коллбэки.
 * @returns Объект с методами {@link ManagedWsConnection.send} и {@link ManagedWsConnection.destroy}.
 */
export function createManagedWs(options: ManagedWsOptions): ManagedWsConnection {
  let destroyed = false;
  let currentWs: WebSocket | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let delay = WS_RECONNECT_BASE_DELAY_MS;

  /** Счётчик последовательных неудачных попыток в текущей серии переподключений. */
  let failedAttempts = 0;
  const errorThreshold = options.errorThresholdAttempts ?? WS_ERROR_THRESHOLD_ATTEMPTS;

  /**
   * Вызывается при каждом разрыве соединения.
   * Управляет переходами между состояниями «переподключение» и «ошибка».
   */
  function handleFailure(): void {
    failedAttempts++;
    if (failedAttempts === 1) {
      // Первый разрыв — тихо переподключаемся, показываем skeleton
      options.onReconnecting?.();
    } else if (failedAttempts === errorThreshold) {
      // Порог исчерпан — сигнализируем ошибку пользователю
      options.onError?.(
        classifyError(new TypeError('WebSocket connection failed'), options.source ?? 'unknown')
      );
    }
    // При failedAttempts > threshold продолжаем фоновые попытки без дополнительных
    // вызовов onError — заголовок с ошибкой уже показан.
  }

  // ── Вспомогательные функции ─────────────────────────────────────────

  function clearTimer(): void {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  /**
   * Планирует следующую попытку подключения.
   *
   * Фаза 1 — нормальный reconnect (failedAttempts < threshold):
   *   Exponential backoff с jitter. Jitter снижает thundering-herd при
   *   одновременном переподключении множества клиентов.
   *
   * Фаза 2 — recovery (failedAttempts >= threshold):
   *   Фиксированный интервал {@link WS_RECOVERY_INTERVAL_MS} без jitter.
   *   Backoff уже не нужен: ошибка показана, задача — тихо опрашивать сервер
   *   по предсказуемому расписанию и восстановиться как только он поднимется.
   */
  function scheduleReconnect(): void {
    if (destroyed) return;

    if (failedAttempts >= errorThreshold) {
      // Recovery-режим: фиксированный интервал, без jitter
      timer = setTimeout(() => connect(), WS_RECOVERY_INTERVAL_MS);
    } else {
      // Нормальный reconnect: экспоненциальный backoff с jitter
      const jitteredDelay = Math.round(delay * (1 + Math.random() * WS_RECONNECT_JITTER));
      timer = setTimeout(() => {
        delay = Math.min(delay * 2, WS_RECONNECT_MAX_DELAY_MS);
        connect();
      }, jitteredDelay);
    }
  }

  // ── Основной connect-цикл ───────────────────────────────────────────

  function connect(): void {
    if (destroyed) return;

    try {
      const url = typeof options.url === 'function' ? options.url() : options.url;

      if (import.meta.env.DEV) {
         
        console.log(`[ws] connecting → ${url.replace(/\?token=.*/, '?token=***')}`);
      }

      const ws = new WebSocket(url);
      currentWs = ws;

      ws.onopen = () => {
        if (import.meta.env.DEV) {
           
          console.log(`[ws] open ← ${url.replace(/\?token=.*/, '?token=***')}`);
        }
        // Успешное подключение: сбрасываем счётчик неудач и задержку
        failedAttempts = 0;
        delay = WS_RECONNECT_BASE_DELAY_MS;
        options.onRecovered?.();
        options.onOpen?.(ws);
      };

      ws.onmessage = (e) => options.onMessage(e);

      ws.onclose = () => {
        if (import.meta.env.DEV) {
           
          console.log(`[ws] close ← ${url.replace(/\?token=.*/, '?token=***')}`);
        }
        handleFailure();
        scheduleReconnect();
      };

      // onerror не закрывает соединение автоматически — закрываем явно,
      // что инициирует onclose, а значит и scheduleReconnect.
      ws.onerror = () => ws.close();
    } catch {
      // WebSocket-конструктор выбросил исключение (невалидный URL и т.п.)
      if (import.meta.env.DEV) {
         
        console.error('[ws] constructor failed');
      }
      handleFailure();
      scheduleReconnect();
    }
  }

  // ── Старт ──────────────────────────────────────────────────────────
  connect();

  // ── Публичный API ──────────────────────────────────────────────────
  return {
    send(data: string): void {
      if (currentWs?.readyState === WebSocket.OPEN) {
        currentWs.send(data);
      }
    },

    destroy(): void {
      destroyed = true;
      clearTimer();

      const ws = currentWs;
      currentWs = null;

      if (ws !== null) {
        // Обнуляем обработчики ДО close(), чтобы onclose не инициировал reconnect
        ws.onopen = null;
        ws.onmessage = null;
        ws.onclose = null;
        ws.onerror = null;
        // Закрываем только активные состояния; вызов на CLOSED/CLOSING — безопасен
        // но браузер пишет предупреждение на CONNECTING, поэтому проверяем явно.
        if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
          ws.close();
        }
      }
    },
  };
}
