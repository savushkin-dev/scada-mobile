import { describe, expect, it } from 'vitest';
import { ZodError, z } from 'zod';
import { classifyError } from './classifyError';
import { AuthSessionExpiredError, HttpError } from './AppError';
import type { AppErrorSource } from './AppError';

const SOURCE: AppErrorSource = 'profile';

describe('classifyError — source и raw пробрасываются', () => {
  it('source сохраняется, raw — строковое сообщение', () => {
    const result = classifyError(new Error('boom'), SOURCE);
    expect(result.source).toBe('profile');
    expect(result.raw).toBe('boom');
  });

  it('не-Error raw превращается в строку', () => {
    expect(classifyError(42, 'unknown').raw).toBe('42');
  });
});

describe('classifyError — AuthSessionExpiredError', () => {
  it('session_expired, critical, без retry', () => {
    const r = classifyError(new AuthSessionExpiredError(), SOURCE);
    expect(r.code).toBe('session_expired');
    expect(r.severity).toBe('critical');
    expect(r.retryable).toBe(false);
  });
});

describe('classifyError — HttpError по статусам', () => {
  it('401 → client_error, critical, без retry', () => {
    const r = classifyError(new HttpError(401), SOURCE);
    expect(r.code).toBe('client_error');
    expect(r.severity).toBe('critical');
    expect(r.retryable).toBe(false);
  });

  it('403 → client_error, critical, без retry', () => {
    const r = classifyError(new HttpError(403), SOURCE);
    expect(r.code).toBe('client_error');
    expect(r.severity).toBe('critical');
    expect(r.retryable).toBe(false);
  });

  it('404 → not_found, degraded, без retry', () => {
    const r = classifyError(new HttpError(404), SOURCE);
    expect(r.code).toBe('not_found');
    expect(r.severity).toBe('degraded');
    expect(r.retryable).toBe(false);
  });

  it('409 → client_error, degraded, без retry', () => {
    const r = classifyError(new HttpError(409), SOURCE);
    expect(r.code).toBe('client_error');
    expect(r.severity).toBe('degraded');
    expect(r.retryable).toBe(false);
  });

  it('500 → server_error, degraded, с retry', () => {
    const r = classifyError(new HttpError(500), SOURCE);
    expect(r.code).toBe('server_error');
    expect(r.severity).toBe('degraded');
    expect(r.retryable).toBe(true);
  });

  it('503 → server_error, с retry', () => {
    expect(classifyError(new HttpError(503), SOURCE).code).toBe('server_error');
  });
});

describe('classifyError — ZodError', () => {
  it('validation_error, degraded, с retry', () => {
    const result = z.object({ a: z.string() }).safeParse({ a: 5 });
    expect(result.success).toBe(false);
    const r = classifyError((result as { error: ZodError }).error, 'profile');
    expect(r.code).toBe('validation_error');
    expect(r.severity).toBe('degraded');
    expect(r.retryable).toBe(true);
  });
});

describe('classifyError — сетевые и прочие ошибки', () => {
  it('TypeError fetch failed → network_unavailable, с retry', () => {
    const r = classifyError(new TypeError('fetch failed'), 'ws/live');
    expect(r.code).toBe('network_unavailable');
    expect(r.severity).toBe('degraded');
    expect(r.retryable).toBe(true);
  });

  it('TypeError Load failed → network_unavailable', () => {
    expect(classifyError(new TypeError('Load failed'), SOURCE).code).toBe('network_unavailable');
  });

  it('TypeError без сетевого сообщения → unknown (fallback)', () => {
    const r = classifyError(new TypeError('cannot read property x'), SOURCE);
    expect(r.code).toBe('unknown');
  });

  it('SyntaxError → parse_error, с retry', () => {
    const r = classifyError(new SyntaxError('Unexpected token'), SOURCE);
    expect(r.code).toBe('parse_error');
    expect(r.retryable).toBe(true);
  });

  it('AbortError → timeout, transient, с retry', () => {
    const err = new Error('aborted');
    err.name = 'AbortError';
    const r = classifyError(err, SOURCE);
    expect(r.code).toBe('timeout');
    expect(r.severity).toBe('transient');
    expect(r.retryable).toBe(true);
  });

  it('RenderError → render_crash, critical', () => {
    const err = new Error('render');
    err.name = 'RenderError';
    const r = classifyError(err, 'ui/render');
    expect(r.code).toBe('render_crash');
    expect(r.severity).toBe('critical');
    expect(r.source).toBe('ui/render');
  });

  it('обычная Error → unknown, с retry', () => {
    const r = classifyError(new Error('что-то странное'), SOURCE);
    expect(r.code).toBe('unknown');
    expect(r.severity).toBe('degraded');
    expect(r.retryable).toBe(true);
  });

  it('не-Error (строка) → unknown', () => {
    expect(classifyError('случилось плохое', SOURCE).code).toBe('unknown');
  });
});
