import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import type { AlertWsMessage } from '../types';

const MAX_RECONNECT_DELAY_MS = 30_000;

export function useAlertsWs(onAlert: (msg: AlertWsMessage) => void) {
  const wsRef = useRef<WebSocket | null>(null);
  const delayRef = useRef(2000);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onAlertRef = useRef(onAlert);
  onAlertRef.current = onAlert;

  useEffect(() => {
    let destroyed = false;

    function connect() {
      if (destroyed) return;
      try {
        const ws = new WebSocket(`${WS_BASE}/ws/alerts`);
        wsRef.current = ws;
        ws.onopen = () => {
          delayRef.current = 2000;
        };
        ws.onmessage = (e) => {
          try {
            const msg = JSON.parse(e.data as string) as AlertWsMessage;
            if (msg.type === 'ALERT') onAlertRef.current(msg);
          } catch {
            /* ignore parse errors */
          }
        };
        ws.onclose = () => {
          if (destroyed) return;
          timerRef.current = setTimeout(() => {
            delayRef.current = Math.min(delayRef.current * 2, MAX_RECONNECT_DELAY_MS);
            connect();
          }, delayRef.current);
        };
        ws.onerror = () => ws.close();
      } catch {
        timerRef.current = setTimeout(() => {
          delayRef.current = Math.min(delayRef.current * 2, MAX_RECONNECT_DELAY_MS);
          connect();
        }, delayRef.current);
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
  }, []);
}
