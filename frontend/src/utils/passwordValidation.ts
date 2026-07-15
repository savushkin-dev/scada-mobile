const MIN_LENGTH = 6;
const MAX_LENGTH = 20;

// Буквы (латиница/кириллица) и цифры; запрещены пробелы, спецсимволы и символы других алфавитов
const ALLOWED_CHARS_REGEX = /^[\p{L}\p{N}]+$/u;
const HAS_LETTER_REGEX = /\p{L}/u;
const HAS_DIGIT_REGEX = /\p{N}/u;

export interface PasswordValidationResult {
  valid: boolean;
  error: string | null;
}

export function validatePassword(password: string): PasswordValidationResult {
  if (password.length < MIN_LENGTH || password.length > MAX_LENGTH) {
    return {
      valid: false,
      error: `Пароль должен содержать от ${MIN_LENGTH} до ${MAX_LENGTH} символов`,
    };
  }
  if (!ALLOWED_CHARS_REGEX.test(password)) {
    return {
      valid: false,
      error: 'Пароль может содержать только буквы и цифры',
    };
  }
  if (!HAS_LETTER_REGEX.test(password)) {
    return { valid: false, error: 'Пароль должен содержать хотя бы одну букву' };
  }
  if (!HAS_DIGIT_REGEX.test(password)) {
    return { valid: false, error: 'Пароль должен содержать хотя бы одну цифру' };
  }
  return { valid: true, error: null };
}

export function validatePasswordMatch(
  password: string,
  confirmation: string
): PasswordValidationResult {
  if (password !== confirmation) {
    return { valid: false, error: 'Пароли не совпадают' };
  }
  return { valid: true, error: null };
}
