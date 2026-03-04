import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import type { UnitsStatusMessage } from '../types';

const MAX_RECONNECT_DELAY_MS = 30_000;

/**
 * Подключается к WebSocket-каналу {@code /ws/workshops/{workshopId}/units/status}
 * и вызывает {@code onMessage} при каждом входящем сообщении.
 *
 * Особенности:
 * - Только чтение: клиент ничего не отправляет на сервер.
 * - При смене {@code workshopId} старое соединение закрывается, открывается новое.
 * - При {@code workshopId === null} хук не создаёт соединения.
 * - Автоматический reconnect с экспоненциальной задержкой + jitter.
 * - Монтируется в {@code WorkshopPage} — живёт пока открыт экран цеха.
 *
 * @param workshopId ID цеха для подписки, или {@code null} для отключения.
 * @param onMessage  Callback, вызываемый при каждом новом сообщении статуса аппаратов.
 */
export function useUnitsStatusWs(
  workshopId: string | null,
  onMessage: (msg: UnitsStatusMessage) => void
) {
  const wsRef = useRef<WebSocket | null>(null);
  const delayRef = useRef(2000);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!workshopId) return;

    let destroyed = false;
    delayRef.current = 2000;

    function connect() {
      if (destroyed) return;
      try {
        const ws = new WebSocket(`${WS_BASE}/ws/workshops/${workshopId}/units/status`);
        wsRef.current = ws;
        ws.onopen = () => {
          delayRef.current = 2000;
        };
        ws.onmessage = (e) => {
          try {
            const msg = JSON.parse(e.data as string) as UnitsStatusMessage;
            if (msg.type === 'UNITS_STATUS') onMessageRef.current(msg);
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
  }, [workshopId]); // переподключается только при смене цеха
}
