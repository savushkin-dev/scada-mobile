import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import type { WorkshopsStatusMessage } from '../types';

const MAX_RECONNECT_DELAY_MS = 30_000;

/**
 * Подключается к WebSocket-каналу {@code /ws/workshops/status} и
 * вызывает {@code onMessage} при каждом входящем сообщении.
 *
 * Особенности:
 * - Только чтение: клиент ничего не отправляет на сервер.
 * - Автоматический reconnect с экспоненциальной задержкой + jitter.
 * - Stable ref для callback'а — хук не переподключается при смене функции.
 * - Монтируется один раз на уровне App — живёт всё время работы приложения.
 */
export function useWorkshopsStatusWs(onMessage: (msg: WorkshopsStatusMessage) => void) {
  const wsRef = useRef<WebSocket | null>(null);
  const delayRef = useRef(2000);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    let destroyed = false;

    function connect() {
      if (destroyed) return;
      try {
        const ws = new WebSocket(`${WS_BASE}/ws/workshops/status`);
        wsRef.current = ws;
        ws.onopen = () => {
          delayRef.current = 2000;
        };
        ws.onmessage = (e) => {
          try {
            const msg = JSON.parse(e.data as string) as WorkshopsStatusMessage;
            if (msg.type === 'WORKSHOPS_STATUS') onMessageRef.current(msg);
          } catch {
            /* ignore parse errors */
          }
        };
        ws.onclose = () => {
          if (destroyed) return;
          const jitter = Math.random() * 0.25;
          timerRef.current = setTimeout(
            () => {
              delayRef.current = Math.min(delayRef.current * 2, MAX_RECONNECT_DELAY_MS);
              connect();
            },
            Math.round(delayRef.current * (1 + jitter))
          );
        };
        ws.onerror = () => ws.close();
      } catch {
        const jitter = Math.random() * 0.25;
        timerRef.current = setTimeout(
          () => {
            delayRef.current = Math.min(delayRef.current * 2, MAX_RECONNECT_DELAY_MS);
            connect();
          },
          Math.round(delayRef.current * (1 + jitter))
        );
      }
    }

    connect();

    return () => {
      destroyed = true;
      if (timerRef.current) clearTimeout(timerRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.onerror = null;
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, []); // монтируется один раз
}
