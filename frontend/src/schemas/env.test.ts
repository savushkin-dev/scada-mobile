import { describe, expect, it } from 'vitest';
import { EnvSchema } from './env';

describe('EnvSchema', () => {
  it('пустой объект парсится (все поля опциональны)', () => {
    expect(EnvSchema.parse({})).toEqual({});
  });

  it('http(s)-API и ws(s)-URL парсятся', () => {
    const parsed = EnvSchema.parse({
      VITE_API_BASE: 'http://192.168.1.10:8080',
      VITE_WS_BASE: 'wss://scada.local/ws',
      VITE_USER_ID: 'user-1',
    });
    expect(parsed.VITE_API_BASE).toBe('http://192.168.1.10:8080');
    expect(parsed.VITE_WS_BASE).toBe('wss://scada.local/ws');
  });

  it('VITE_API_BASE без http(s):// — отказ', () => {
    expect(EnvSchema.safeParse({ VITE_API_BASE: 'scada.local:8080' }).success).toBe(false);
  });

  it('VITE_API_BASE с ws:// — отказ (ожидается http)', () => {
    expect(EnvSchema.safeParse({ VITE_API_BASE: 'ws://scada.local' }).success).toBe(false);
  });

  it('VITE_WS_BASE с http:// — отказ (ожидается ws)', () => {
    expect(EnvSchema.safeParse({ VITE_WS_BASE: 'http://scada.local/ws' }).success).toBe(false);
  });

  it('пустой VITE_USER_ID — отказ', () => {
    expect(EnvSchema.safeParse({ VITE_USER_ID: '' }).success).toBe(false);
  });

  it('число вместо строки — отказ', () => {
    expect(EnvSchema.safeParse({ VITE_API_BASE: 8080 }).success).toBe(false);
  });
});
