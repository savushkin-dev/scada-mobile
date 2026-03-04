import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import type { UnitWsMessage } from '../types';
import { MOCK_UNIT_MESSAGES } from '../constants/mockData';

const MAX_RECONNECT_DELAY_MS = 30_000;

export function useUnitWs(unitId: string | null, onMessage: (msg: UnitWsMessage) => void) {
  const wsRef = useRef<WebSocket | null>(null);
  const delayRef = useRef(2000);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mockFiredRef = useRef(false);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!unitId) return;

    let destroyed = false;
    delayRef.current = 2000;
    mockFiredRef.current = false;

    function applyMockData() {
      if (mockFiredRef.current) return;
      mockFiredRef.current = true;
      MOCK_UNIT_MESSAGES.forEach((msg) => onMessageRef.current(msg));
    }

    function connect() {
      if (destroyed) return;
      try {
        const ws = new WebSocket(`${WS_BASE}/ws/unit/${unitId}`);
        wsRef.current = ws;
        ws.onopen = () => {
          delayRef.current = 2000;
        };
        ws.onmessage = (e) => {
          try {
            const msg = JSON.parse(e.data as string) as UnitWsMessage;
            onMessageRef.current(msg);
          } catch {
            /* ignore parse errors */
          }
        };
        ws.onclose = () => {
          if (destroyed) return;
          applyMockData();
          timerRef.current = setTimeout(() => {
            delayRef.current = Math.min(delayRef.current * 2, MAX_RECONNECT_DELAY_MS);
            connect();
          }, delayRef.current);
        };
        ws.onerror = () => ws.close();
      } catch {
        applyMockData();
      }
    }

    applyMockData();
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
  }, [unitId]);
}
