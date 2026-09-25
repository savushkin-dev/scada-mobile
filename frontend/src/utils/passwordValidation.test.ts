import { describe, expect, it } from 'vitest';
import { validatePassword, validatePasswordMatch } from './passwordValidation';

describe('validatePassword — длина', () => {
  it('5 символов — отказ', () => {
    expect(validatePassword('abc12').valid).toBe(false);
  });

  it('6 символов — граница, допустимо (буквы+цифры)', () => {
    expect(validatePassword('abc123')).toEqual({ valid: true, error: null });
  });

  it('20 символов — граница, допустимо', () => {
    expect(validatePassword('abcdefghij1234567890'.slice(0, 20))).toEqual({
      valid: true,
      error: null,
    });
  });

  it('21 символ — отказ', () => {
    expect(validatePassword('abcdefghij12345678901'.slice(0, 21)).valid).toBe(false);
  });
});

describe('validatePassword — состав', () => {
  it('только буквы — отказ (нет цифры)', () => {
    const r = validatePassword('abcdef');
    expect(r.valid).toBe(false);
    expect(r.error).toBe('Пароль должен содержать хотя бы одну цифру');
  });

  it('только цифры — отказ (нет буквы)', () => {
    const r = validatePassword('123456');
    expect(r.valid).toBe(false);
    expect(r.error).toBe('Пароль должен содержать хотя бы одну букву');
  });

  it('кириллица + цифры — допустимо', () => {
    expect(validatePassword('пароль123')).toEqual({ valid: true, error: null });
  });

  it('пробел внутри — отказ', () => {
    expect(validatePassword('abc 123').valid).toBe(false);
  });

  it('спецсимвол — отказ', () => {
    expect(validatePassword('abc123!').valid).toBe(false);
  });

  it('подчёркивание — отказ', () => {
    expect(validatePassword('abc_123').valid).toBe(false);
  });

  it('смесь латиницы и кириллицы с цифрой — допустимо', () => {
    expect(validatePassword('abcф12').valid).toBe(true);
  });
});

describe('validatePasswordMatch', () => {
  it('совпадающие пароли — допустимо', () => {
    expect(validatePasswordMatch('abc123', 'abc123')).toEqual({ valid: true, error: null });
  });

  it('разные пароли — отказ', () => {
    expect(validatePasswordMatch('abc123', 'abc124')).toEqual({
      valid: false,
      error: 'Пароли не совпадают',
    });
  });
});
