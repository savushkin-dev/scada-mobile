// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ManagedWsOptions } from '../lib/createManagedWs';
import { useLiveWs } from './useLiveWs';

const mocks = vi.hoisted(() => ({
  createManagedWs: vi.fn(),
  getAccessToken: vi.fn(),
  refreshAccessToken: vi.fn(),
}));

vi.mock('../lib/createManagedWs', () => ({
  createManagedWs: mocks.createManagedWs,
}));

vi.mock('../auth/session', () => ({
  getAccessToken: mocks.getAccessToken,
}));

vi.mock('../api/auth', () => ({
  refreshAccessToken: mocks.refreshAccessToken,
  loginUser: vi.fn(),
}));

type CapturedOptions = ManagedWsOptions;

let conn: { send: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> };

function lastOptions(): CapturedOptions {
  const calls = mocks.createManagedWs.mock.calls;
  const call = calls[calls.length - 1];
  if (!call) throw new Error('createManagedWs не вызван');
  return call[0] as CapturedOptions;
}

const baseCallbacks = {
  onAlertSnapshot: vi.fn(),
  onNotificationSnapshot: vi.fn(),
  onUnitsStatus: vi.fn(),
  onAlert: vi.fn(),
  onNotification: vi.fn(),
  onError: vi.fn(),
  onRecovered: vi.fn(),
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('useLiveWs', () => {
  beforeEach(() => {
    mocks.getAccessToken.mockReturnValue('token-1');
    conn = { send: vi.fn(), destroy: vi.fn() };
    mocks.createManagedWs.mockReturnValue(conn);
  });

  it('без токена соединение не создаётся', () => {
    mocks.getAccessToken.mockReturnValue(null);
    renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    expect(mocks.createManagedWs).not.toHaveBeenCalled();
  });

  it('с токеном создаёт соединение; url содержит /ws/live и токен', () => {
    renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    const options = lastOptions();
    expect(typeof options.url).toBe('function');
    expect((options.url as () => string)()).toContain('/ws/live');
    expect((options.url as () => string)()).toContain('token=token-1');
  });

  it('onOpen восстанавливает подписку на цех через ws.send', () => {
    renderHook(() => useLiveWs(7, 'u1', baseCallbacks));
    const options = lastOptions();
    const ws = { send: vi.fn() };
    options.onOpen?.(ws as unknown as WebSocket);
    expect(ws.send).toHaveBeenCalledWith(
      JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId: 7 })
    );
  });

  it('onOpen без подписки на цех ничего не отправляет', () => {
    renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    const options = lastOptions();
    const ws = { send: vi.fn() };
    options.onOpen?.(ws as unknown as WebSocket);
    expect(ws.send).not.toHaveBeenCalled();
  });

  it('валидное UNITS_STATUS-сообщение вызывает onUnitsStatus и onRecovered', () => {
    renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    const options = lastOptions();
    options.onMessage(
      new MessageEvent('message', {
        data: JSON.stringify({ type: 'UNITS_STATUS', workshopId: 5, payload: [] }),
      })
    );
    expect(baseCallbacks.onUnitsStatus).toHaveBeenCalledTimes(1);
    expect(baseCallbacks.onRecovered).toHaveBeenCalled();
  });

  it('невалидный JSON вызывает onError', () => {
    renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    const options = lastOptions();
    options.onMessage(new MessageEvent('message', { data: '{broken' }));
    expect(baseCallbacks.onError).toHaveBeenCalledTimes(1);
  });

  it('неизвестный тип сообщения молча пропускается', () => {
    renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    const options = lastOptions();
    options.onMessage(
      new MessageEvent('message', { data: JSON.stringify({ type: 'NO_SUCH_TYPE', x: 1 }) })
    );
    expect(baseCallbacks.onAlert).not.toHaveBeenCalled();
    expect(baseCallbacks.onUnitsStatus).not.toHaveBeenCalled();
    expect(baseCallbacks.onError).not.toHaveBeenCalled();
  });

  it('при размонтировании вызывается destroy', () => {
    const { unmount } = renderHook(() => useLiveWs(null, 'u1', baseCallbacks));
    unmount();
    expect(conn.destroy).toHaveBeenCalledTimes(1);
  });
});
