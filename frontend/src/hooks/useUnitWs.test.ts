// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ManagedWsOptions } from '../lib/createManagedWs';
import { useUnitWs } from './useUnitWs';

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

let conn: { send: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> };

function lastOptions(): ManagedWsOptions {
  const calls = mocks.createManagedWs.mock.calls;
  const call = calls[calls.length - 1];
  if (!call) throw new Error('createManagedWs не вызван');
  return call[0] as ManagedWsOptions;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('useUnitWs', () => {
  beforeEach(() => {
    mocks.getAccessToken.mockReturnValue('token-1');
    conn = { send: vi.fn(), destroy: vi.fn() };
    mocks.createManagedWs.mockReturnValue(conn);
  });

  it('unitId=null — соединение не создаётся', () => {
    renderHook(() => useUnitWs(null, 'u1', vi.fn()));
    expect(mocks.createManagedWs).not.toHaveBeenCalled();
  });

  it('userId=null — соединение не создаётся', () => {
    renderHook(() => useUnitWs('42', null, vi.fn()));
    expect(mocks.createManagedWs).not.toHaveBeenCalled();
  });

  it('unitId и userId заданы — соединение к /ws/unit/{unitId} с токеном', () => {
    renderHook(() => useUnitWs('42', 'u1', vi.fn()));
    expect(mocks.createManagedWs).toHaveBeenCalledTimes(1);
    const options = lastOptions();
    expect((options.url as () => string)()).toContain('/ws/unit/42');
    expect((options.url as () => string)()).toContain('token=token-1');
  });

  it('валидное LINE_STATUS-сообщение передаётся в onMessage', () => {
    const onMessage = vi.fn();
    renderHook(() => useUnitWs('42', 'u1', onMessage));
    const options = lastOptions();
    const payload = { type: 'LINE_STATUS', payload: { batchNumber: 'БН-1' } };
    options.onMessage(new MessageEvent('message', { data: JSON.stringify(payload) }));
    expect(onMessage).toHaveBeenCalledTimes(1);
    expect(onMessage.mock.calls[0][0]).toMatchObject({ type: 'LINE_STATUS' });
  });

  it('невалидный JSON — вызван onError, onMessage не вызван', () => {
    const onMessage = vi.fn();
    const onError = vi.fn();
    renderHook(() => useUnitWs('42', 'u1', onMessage, { onError }));
    const options = lastOptions();
    options.onMessage(new MessageEvent('message', { data: '{oops' }));
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onMessage).not.toHaveBeenCalled();
  });

  it('структурно невалидное сообщение молча пропускается', () => {
    const onMessage = vi.fn();
    const onError = vi.fn();
    renderHook(() => useUnitWs('42', 'u1', onMessage, { onError }));
    const options = lastOptions();
    options.onMessage(
      new MessageEvent('message', { data: JSON.stringify({ type: 'UNKNOWN_TYPE' }) })
    );
    expect(onMessage).not.toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
  });

  it('при размонтировании вызывается destroy', () => {
    const { unmount } = renderHook(() => useUnitWs('42', 'u1', vi.fn()));
    unmount();
    expect(conn.destroy).toHaveBeenCalledTimes(1);
  });
});
