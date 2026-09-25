import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createManagedWs } from './createManagedWs';
import { WS_RECOVERY_INTERVAL_MS } from '../config';

type WsHandler = ((event?: unknown) => void) | null;

class FakeWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  static instances: FakeWebSocket[] = [];

  readonly url: string;
  readyState = FakeWebSocket.CONNECTING;
  onopen: WsHandler = null;
  onmessage: WsHandler = null;
  onclose: WsHandler = null;
  onerror: WsHandler = null;
  sent: string[] = [];
  closedTimes = 0;

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.instances.push(this);
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.({ type: 'open' });
  }

  emitMessage(data: string): void {
    this.onmessage?.({ data });
  }

  close(): void {
    this.closedTimes++;
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.({ type: 'close' });
  }

  send(data: string): void {
    this.sent.push(data);
  }
}

beforeEach(() => {
  FakeWebSocket.instances = [];
  vi.stubGlobal('WebSocket', FakeWebSocket);
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('createManagedWs', () => {
  it('подключается по переданному URL, open вызывает onRecovered и onOpen', () => {
    const onOpen = vi.fn();
    const onRecovered = vi.fn();
    createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn(), onOpen, onRecovered });

    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(FakeWebSocket.instances[0].url).toBe('ws://test/ws');

    FakeWebSocket.instances[0].open();
    expect(onRecovered).toHaveBeenCalledTimes(1);
    expect(onOpen).toHaveBeenCalledTimes(1);
    expect(onOpen).toHaveBeenCalledWith(FakeWebSocket.instances[0]);
  });

  it('url как функция вызывается для получения адреса', () => {
    const url = vi.fn(() => 'ws://test/from-fn');
    createManagedWs({ url, onMessage: vi.fn() });
    expect(url).toHaveBeenCalled();
    expect(FakeWebSocket.instances[0].url).toBe('ws://test/from-fn');
  });

  it('входящее сообщение передаётся в onMessage как есть', () => {
    const onMessage = vi.fn();
    createManagedWs({ url: 'ws://test/ws', onMessage });
    const ws = FakeWebSocket.instances[0];
    ws.open();
    ws.emitMessage('{"a":1}');
    expect(onMessage).toHaveBeenCalledTimes(1);
    expect(onMessage.mock.calls[0][0]).toEqual({ data: '{"a":1}' });
  });

  it('send передаёт данные открытому сокету; на закрытом — noop', () => {
    const conn = createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn() });
    const ws = FakeWebSocket.instances[0];

    conn.send('рано');
    expect(ws.sent).toHaveLength(0);

    ws.open();
    conn.send('ping');
    expect(ws.sent).toEqual(['ping']);

    ws.close();
    conn.send('поздно');
    expect(ws.sent).toEqual(['ping']);
  });

  it('сервер закрыл сокет — вызван onReconnecting, после таймера создан новый инстанс', () => {
    const onReconnecting = vi.fn();
    createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn(), onReconnecting });

    FakeWebSocket.instances[0].open();
    FakeWebSocket.instances[0].close();

    expect(onReconnecting).toHaveBeenCalledTimes(1);
    expect(FakeWebSocket.instances).toHaveLength(1);

    vi.advanceTimersByTime(5000);
    expect(FakeWebSocket.instances).toHaveLength(2);
  });

  it('порог ошибок: при errorThresholdAttempts=2 второй разрыв вызывает onError', () => {
    const onError = vi.fn();
    createManagedWs({
      url: 'ws://test/ws',
      onMessage: vi.fn(),
      onError,
      errorThresholdAttempts: 2,
    });

    FakeWebSocket.instances[0].close();
    expect(onError).not.toHaveBeenCalled();

    vi.advanceTimersByTime(5000);
    FakeWebSocket.instances[1].close();
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('порог ошибок: при errorThresholdAttempts=1 первый же разрыв вызывает onError (регрессия)', () => {
    const onError = vi.fn();
    const onReconnecting = vi.fn();
    createManagedWs({
      url: 'ws://test/ws',
      onMessage: vi.fn(),
      onError,
      onReconnecting,
      errorThresholdAttempts: 1,
    });

    FakeWebSocket.instances[0].close();
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onReconnecting).not.toHaveBeenCalled();
  });

  it('после исчерпания порога реконнект идёт по фиксированному recovery-интервалу', () => {
    const onError = vi.fn();
    createManagedWs({
      url: 'ws://test/ws',
      onMessage: vi.fn(),
      onError,
      errorThresholdAttempts: 2,
    });

    FakeWebSocket.instances[0].close();
    expect(onError).not.toHaveBeenCalled();

    vi.advanceTimersByTime(5000);
    FakeWebSocket.instances[1].close();
    expect(onError).toHaveBeenCalledTimes(1);

    // recovery-интервал больше базовой задержки backoff — проверяем именно его
    vi.advanceTimersByTime(WS_RECOVERY_INTERVAL_MS - 1);
    expect(FakeWebSocket.instances).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(FakeWebSocket.instances).toHaveLength(3);
  });

  it('destroy после разрыва отменяет ожидающий reconnect — новых подключений нет', () => {
    const conn = createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn() });
    FakeWebSocket.instances[0].close();

    conn.destroy();
    vi.advanceTimersByTime(60_000);
    expect(FakeWebSocket.instances).toHaveLength(1);
  });

  it('destroy до открытия закрывает CONNECTING-сокет без вызова onclose-обработчиков', () => {
    const onReconnecting = vi.fn();
    const conn = createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn(), onReconnecting });
    const ws = FakeWebSocket.instances[0];

    conn.destroy();
    expect(ws.closedTimes).toBe(1);

    vi.advanceTimersByTime(60_000);
    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(onReconnecting).not.toHaveBeenCalled();
  });

  it('ошибка сокета закрывает его, что инициирует reconnect', () => {
    const onReconnecting = vi.fn();
    createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn(), onReconnecting });
    const ws = FakeWebSocket.instances[0];
    ws.open();

    ws.onerror?.({ type: 'error' });
    expect(ws.closedTimes).toBe(1);
    expect(onReconnecting).toHaveBeenCalledTimes(1);
  });

  it('onBeforeConnect вызывается перед подключением', async () => {
    const onBeforeConnect = vi.fn().mockResolvedValue(undefined);
    createManagedWs({ url: 'ws://test/ws', onMessage: vi.fn(), onBeforeConnect });
    for (let i = 0; i < 5; i++) await Promise.resolve();
    expect(onBeforeConnect).toHaveBeenCalled();
  });
});
