import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import { createManagedWs } from '../lib/createManagedWs';
import type { UnitWsMessage } from '../types';

/**
 * Подключается к WebSocket-каналу {@code /ws/unit/{unitId}} и вызывает
 * {@code onMessage} при каждом входящем сообщении.
 *
 * Использует {@link createManagedWs} — reconnect с exponential backoff + jitter,
 * корректный cleanup при размонтировании.
 *
 * @param unitId    ID аппарата для подписки, или {@code null} для отключения.
 * @param onMessage Callback, вызываемый при каждом новом сообщении.
 */
export function useUnitWs(unitId: string | null, onMessage: (msg: UnitWsMessage) => void): void {
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!unitId) return;

    const conn = createManagedWs({
      url: `${WS_BASE}/ws/unit/${unitId}`,
      onMessage: (e) => {
        try {
          const msg = JSON.parse(e.data as string) as UnitWsMessage;
          onMessageRef.current(msg);
        } catch {
          /* ignore parse errors */
        }
      },
    });

    return () => conn.destroy();
  }, [unitId]);
}
