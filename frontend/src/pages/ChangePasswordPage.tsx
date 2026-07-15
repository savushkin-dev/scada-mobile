import { useCallback, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { APP_BRAND } from '../config';
import { changePassword } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { isTemporaryPasswordToken } from '../auth/token';
import { getAccessToken } from '../auth/session';
import { validatePassword, validatePasswordMatch } from '../utils/passwordValidation';

type FieldErrors = {
  password?: string;
  confirmPassword?: string;
};

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const confirmRef = useRef<HTMLInputElement>(null);

  const isTemporary = useMemo(() => isTemporaryPasswordToken(getAccessToken()), []);

  const validate = useCallback((): FieldErrors => {
    const errors: FieldErrors = {};
    const passwordCheck = validatePassword(password);
    if (!passwordCheck.valid) {
      errors.password = passwordCheck.error ?? 'Некорректный пароль';
    }
    const matchCheck = validatePasswordMatch(password, confirmPassword);
    if (!matchCheck.valid) {
      errors.confirmPassword = matchCheck.error ?? 'Пароли не совпадают';
    }
    return errors;
  }, [password, confirmPassword]);

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      if (submitting) return;

      const errors = validate();
      setFieldErrors(errors);
      setFormError(null);
      if (errors.password || errors.confirmPassword) return;

      setSubmitting(true);
      try {
        const result = await changePassword({ newPassword: password });
        login(result.userId, result.role, result.accessToken, result.refreshToken);
        navigate('/', { replace: true });
      } catch (err) {
        const message = err instanceof Error ? err.message : '';
        const statusMatch = message.match(/^(\d+)\|/);
        const status = statusMatch ? parseInt(statusMatch[1], 10) : null;

        if (status != null && status >= 500) {
          setFormError('Ошибка сервера. Попробуйте позже.');
        } else if (
          err instanceof TypeError ||
          message.toLowerCase().includes('fetch') ||
          message.toLowerCase().includes('network')
        ) {
          setFormError('Нет связи с сервером');
        } else {
          const bodyMessage = message.split('|')[1] ?? message;
          setFormError(bodyMessage || 'Не удалось сменить пароль');
        }
      } finally {
        setSubmitting(false);
      }
    },
    [submitting, validate, password, login, navigate]
  );

  const handlePasswordChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setPassword(event.target.value);
      if (fieldErrors.password) {
        setFieldErrors((prev) => ({ ...prev, password: undefined }));
      }
      if (formError) setFormError(null);
    },
    [fieldErrors.password, formError]
  );

  const handleConfirmChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setConfirmPassword(event.target.value);
      if (fieldErrors.confirmPassword) {
        setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }));
      }
      if (formError) setFormError(null);
    },
    [fieldErrors.confirmPassword, formError]
  );

  const handlePasswordKeyDown = useCallback((event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      confirmRef.current?.focus();
    }
  }, []);

  const handleConfirmKeyDown = useCallback((event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }, []);

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-[#f8f9fa]/95 px-5 py-8 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="change-password-title"
    >
      <div className="w-full max-w-[420px] overflow-hidden rounded-[28px] border border-white/70 bg-white/95 shadow-[0_24px_70px_rgba(26,28,30,0.18)]">
        <div className="border-b border-[#f0f0f0] px-6 pb-4 pt-5 sm:px-7">
          <p className="text-[10px] font-bold uppercase tracking-wider text-[#74777F]">
            {APP_BRAND.subtitle}
          </p>
          <h2 id="change-password-title" className="mt-1 text-xl font-bold text-[#1A1C1E]">
            {isTemporary ? 'Смена временного пароля' : 'Смена пароля'}
          </h2>
          {isTemporary && (
            <p className="mt-2 text-sm font-medium text-[#9f3138]">
              Для продолжения работы придумайте собственный пароль.
            </p>
          )}
        </div>

        <form className="space-y-4 px-6 pb-6 pt-5 sm:px-7" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-[#5F6368]" htmlFor="password">
              Новый пароль
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={handlePasswordChange}
              onKeyDown={handlePasswordKeyDown}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              placeholder="Введите новый пароль"
              className={`w-full rounded-2xl border px-4 py-3 text-sm font-medium text-[#1A1C1E] outline-none transition ${
                fieldErrors.password
                  ? 'border-[#e5a3a8] bg-[#fff6f7]'
                  : 'border-[#e7e8ea] bg-white focus:border-[#8fb3ff] focus:ring-2 focus:ring-[#e8f0ff]'
              }`}
            />
            {fieldErrors.password && (
              <p id="password-error" className="text-[11px] font-semibold text-[#9f3138]">
                {fieldErrors.password}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-[#5F6368]" htmlFor="confirmPassword">
              Повторите пароль
            </label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              ref={confirmRef}
              onChange={handleConfirmChange}
              onKeyDown={handleConfirmKeyDown}
              aria-invalid={Boolean(fieldErrors.confirmPassword)}
              aria-describedby={fieldErrors.confirmPassword ? 'confirm-error' : undefined}
              placeholder="Повторите новый пароль"
              className={`w-full rounded-2xl border px-4 py-3 text-sm font-medium text-[#1A1C1E] outline-none transition ${
                fieldErrors.confirmPassword
                  ? 'border-[#e5a3a8] bg-[#fff6f7]'
                  : 'border-[#e7e8ea] bg-white focus:border-[#8fb3ff] focus:ring-2 focus:ring-[#e8f0ff]'
              }`}
            />
            {fieldErrors.confirmPassword && (
              <p id="confirm-error" className="text-[11px] font-semibold text-[#9f3138]">
                {fieldErrors.confirmPassword}
              </p>
            )}
          </div>

          <p className="text-[11px] font-medium text-[#5F6368]">
            Пароль должен содержать от 6 до 20 символов, только буквы и цифры, и хотя бы одну букву
            и одну цифру.
          </p>

          {formError && (
            <div
              role="alert"
              className="rounded-2xl border border-[#f0b4b8] bg-[#fff0f1] px-4 py-3 text-xs font-semibold text-[#9f3138]"
            >
              {formError}
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-2xl bg-[#1A1C1E] px-4 py-3 text-sm font-semibold text-white shadow-sm transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? 'Сохранение...' : 'Сохранить пароль'}
          </button>
        </form>
      </div>
    </div>
  );
}
