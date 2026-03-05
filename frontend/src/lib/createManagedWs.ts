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
} from '../config';

// ── Публичные типы ────────────────────────────────────────────────────

export interface ManagedWsOptions {
  /** URL для подключения (строка, не меняется в течение жизни объекта). */
  url: string;
  /**
   * Вызывается при каждом успешном открытии соединения (включая реконнекты).
   * Идеальное место для восстановления подписок после обрыва.
   * Принимает свежий WebSocket-объект — можно сразу вызвать ws.send().
   */
  onOpen?: (ws: WebSocket) => void;
  /** Вызывается при каждом входящем сообщении. */
  onMessage: (event: MessageEvent) => void;
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

  // ── Вспомогательные функции ─────────────────────────────────────────

  function clearTimer(): void {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  /**
   * Планирует следующую попытку подключения через jittered-exponential delay.
   * Не запускает цикл если destroyed = true.
   */
  function scheduleReconnect(): void {
    if (destroyed) return;
    // Задержка с разбросом ±JITTER для снижения thundering-herd
    const jitteredDelay = Math.round(delay * (1 + Math.random() * WS_RECONNECT_JITTER));
    timer = setTimeout(() => {
      delay = Math.min(delay * 2, WS_RECONNECT_MAX_DELAY_MS);
      connect();
    }, jitteredDelay);
  }

  // ── Основной connect-цикл ───────────────────────────────────────────

  function connect(): void {
    if (destroyed) return;

    try {
      const ws = new WebSocket(options.url);
      currentWs = ws;

      ws.onopen = () => {
        // Успешное подключение: сбрасываем задержку к начальному значению
        delay = WS_RECONNECT_BASE_DELAY_MS;
        options.onOpen?.(ws);
      };

      ws.onmessage = (e) => options.onMessage(e);

      ws.onclose = () => scheduleReconnect();

      // onerror не закрывает соединение автоматически — закрываем явно,
      // что инициирует onclose, а значит и scheduleReconnect.
      ws.onerror = () => ws.close();
    } catch {
      // WebSocket-конструктор выбросил исключение (невалидный URL и т.п.)
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
