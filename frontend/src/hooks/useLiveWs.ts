import { useEffect, useRef } from 'react';
import { WS_BASE } from '../config';
import { classifyError } from '../errors/classifyError';
import type { AppError } from '../errors/AppError';
import { createManagedWs, type ManagedWsConnection } from '../lib/createManagedWs';
import type { AlertWsMessage, UnitsStatusMessage } from '../types';

/**
 * Коллбэки, вызываемые хуком при получении сообщений от сервера.
 * Все ссылки стабилизируются через ref — хук не переподключается при смене функций.
 */
export interface LiveWsCallbacks {
  /** ALERT_SNAPSHOT — начальный срез активных алёртов при подключении */
  onAlertSnapshot: (alerts: AlertWsMessage[]) => void;
  /** UNITS_STATUS — live-статус аппаратов подписанного цеха */
  onUnitsStatus: (msg: UnitsStatusMessage) => void;
  /** ALERT — дельта изменения ошибки (active true/false) */
  onAlert: (msg: AlertWsMessage) => void;
  /**
   * Первый разрыв соединения — начало тихого переподключения.
   * UI должен показывать skeleton, но не ошибку в шапке.
   */
  onReconnecting?: () => void;
  /** Транспортная или parse-ошибка канала /ws/live — исчерпан порог попыток. */
  onError?: (error: AppError) => void;
  /** Соединение восстановлено: socket физически открыт или пришло валидное сообщение. */
  onRecovered?: () => void;
}

/**
 * Единственный WebSocket-хук приложения. Открывает одно постоянное соединение
 * к {@code /ws/live} и мультиплексирует все live-данные через него.
 *
 * <h3>Соединение</h3>
 * Использует {@link createManagedWs} — фабрику с единой стратегией reconnect:
 * exponential backoff + jitter, корректный teardown на unmount.
 *
 * <h3>Протокол</h3>
 * <ul>
 *   <li>При подключении сервер немедленно присылает {@code ALERT_SNAPSHOT}.</li>
 *   <li>Хук подписывается / отписывается от цеха, отправляя JSON-действия серверу.</li>
 *   <li>При реконнекте подписка на текущий цех автоматически восстанавливается
 *       через коллбэк {@code onOpen} фабрики.</li>
 * </ul>
 *
 * <h3>Жизненный цикл</h3>
 * Монтируется один раз на уровне {@code AppInner} — живёт всё время работы приложения.
 * Соединение не закрывается при смене экранов — только меняется подписка на цех.
 *
 * @param subscribedWorkshopId ID цеха для подписки (SUBSCRIBE_WORKSHOP/UNSUBSCRIBE_WORKSHOP),
 *                             или {@code null} когда пользователь не на экране цеха.
 * @param callbacks            Коллбэки для трёх типов входящих сообщений.
 */
export function useLiveWs(subscribedWorkshopId: string | null, callbacks: LiveWsCallbacks): void {
  // Ссылка на управляемое соединение — нужна второму эффекту для отправки сообщений
  const connRef = useRef<ManagedWsConnection | null>(null);

  // Stable refs: хук не пересоздаёт соединение при изменении коллбэков / workshop
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;

  const subscribedWorkshopIdRef = useRef(subscribedWorkshopId);
  subscribedWorkshopIdRef.current = subscribedWorkshopId;

  // Effect 1: открываем соединение один раз при монтировании
  useEffect(() => {
    const conn = createManagedWs({
      url: `${WS_BASE}/ws/live`,
      source: 'ws/live',
      onReconnecting: () => callbacksRef.current.onReconnecting?.(),
      onError: (error) => callbacksRef.current.onError?.(error),
      onRecovered: () => callbacksRef.current.onRecovered?.(),

      // onOpen вызывается при каждом успешном подключении (в т.ч. после реконнекта).
      // Восстанавливаем подписку на цех, если она была активна в момент обрыва.
      onOpen: (ws) => {
        const workshopId = subscribedWorkshopIdRef.current;
        if (workshopId) {
          ws.send(JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId }));
        }
      },

      onMessage: (e) => {
        try {
          const msg = JSON.parse(e.data as string) as { type: string };
          const cb = callbacksRef.current;
          cb.onRecovered?.();
          switch (msg.type) {
            case 'ALERT_SNAPSHOT':
              cb.onAlertSnapshot((msg as { type: string; payload: AlertWsMessage[] }).payload);
              break;
            case 'UNITS_STATUS':
              cb.onUnitsStatus(msg as UnitsStatusMessage);
              break;
            case 'ALERT':
              cb.onAlert(msg as AlertWsMessage);
              break;
            default:
              // Неизвестный тип — игнорируем (forward compat)
              break;
          }
        } catch (error) {
          callbacksRef.current.onError?.(classifyError(error, 'ws/live'));
        }
      },
    });

    connRef.current = conn;

    return () => {
      connRef.current = null;
      conn.destroy();
    };
  }, []); // Монтируется один раз

  // Effect 2: управляем подпиской на цех при изменении subscribedWorkshopId.
  // conn.send() — noop если соединение ещё не открыто;
  // в этом случае onOpen в Effect 1 восстановит подписку при открытии.
  useEffect(() => {
    const conn = connRef.current;
    if (conn === null) return;

    if (subscribedWorkshopId) {
      // SUBSCRIBE_WORKSHOP атомарно заменяет предыдущую подписку на сервере
      conn.send(JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId: subscribedWorkshopId }));
    } else {
      conn.send(JSON.stringify({ action: 'UNSUBSCRIBE_WORKSHOP' }));
    }
  }, [subscribedWorkshopId]);
}
