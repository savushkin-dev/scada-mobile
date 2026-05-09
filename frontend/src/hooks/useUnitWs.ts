import { useEffect, useRef } from 'react';
import { USER_ID, WS_BASE } from '../config';
import { classifyError } from '../errors/classifyError';
import type { AppError } from '../errors/AppError';
import { createManagedWs } from '../lib/createManagedWs';
import { UnitWsMessageSchema } from '../schemas';
import type { UnitWsMessage } from '../types';

interface UnitWsCallbacks {
  onReconnecting?: () => void;
  onError?: (error: AppError) => void;
  onRecovered?: () => void;
}

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
export function useUnitWs(
  unitId: string | null,
  onMessage: (msg: UnitWsMessage) => void,
  callbacks?: UnitWsCallbacks
): void {
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;

  useEffect(() => {
    if (!unitId) return;

    const conn = createManagedWs({
      url: `${WS_BASE}/ws/unit/${unitId}${USER_ID ? `?userId=${encodeURIComponent(USER_ID)}` : ''}`,
      source: 'ws/unit',
      onReconnecting: () => callbacksRef.current?.onReconnecting?.(),
      onError: (error) => callbacksRef.current?.onError?.(error),
      onRecovered: () => callbacksRef.current?.onRecovered?.(),
      onMessage: (e) => {
        let raw: unknown;
        try {
          raw = JSON.parse(e.data as string);
        } catch (error) {
          callbacksRef.current?.onError?.(classifyError(error, 'ws/unit'));
          return;
        }

        const result = UnitWsMessageSchema.safeParse(raw);
        if (!result.success) {
          // forward compat: неизвестный type или структурная ошибка — молча пропускаем.
          if (import.meta.env.DEV) {
            console.warn('[ws/unit] неожиданное сообщение от сервера', result.error.issues);
          }
          return;
        }

        callbacksRef.current?.onRecovered?.();
        onMessageRef.current(result.data);
      },
    });

    return () => {
      conn.destroy();
    };
  }, [unitId]);
}
