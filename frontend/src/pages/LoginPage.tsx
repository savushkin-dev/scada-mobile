import { useCallback, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { APP_BRAND, AUTH_COPY } from '../config';
import { loginUser } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import type { LoginResponse } from '../api/auth';

type FieldErrors = {
  workerCode?: string;
  password?: string;
};

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [workerCode, setWorkerCode] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showHint, setShowHint] = useState(false);
  const passwordRef = useRef<HTMLInputElement>(null);

  const fromPath = useMemo(() => {
    const state = location.state as { from?: { pathname?: string } } | null;
    return state?.from?.pathname ?? '/';
  }, [location.state]);

  const validate = useCallback((): FieldErrors => {
    const errors: FieldErrors = {};
    if (!workerCode.trim()) errors.workerCode = AUTH_COPY.requiredWorkerCode;
    if (!password.trim()) errors.password = AUTH_COPY.requiredPassword;
    return errors;
  }, [workerCode, password]);

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      if (submitting) return;

      const errors = validate();
      setFieldErrors(errors);
      setFormError(null);
      if (errors.workerCode || errors.password) return;

      setSubmitting(true);
      try {
        const result: LoginResponse = await loginUser({
          workerCode: workerCode.trim(),
          password: password.trim(),
        });
        login(result.userId, result.accessToken, result.refreshToken);
        navigate(fromPath, { replace: true });
      } catch {
        setFormError(AUTH_COPY.notFound);
      } finally {
        setSubmitting(false);
      }
    },
    [submitting, validate, workerCode, password, login, navigate, fromPath]
  );

  const handleWorkerCodeChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setWorkerCode(event.target.value);
      if (fieldErrors.workerCode) {
        setFieldErrors((prev) => ({ ...prev, workerCode: undefined }));
      }
      if (formError) setFormError(null);
    },
    [fieldErrors.workerCode, formError]
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

  const handleWorkerCodeKeyDown = useCallback((event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      passwordRef.current?.focus();
    }
  }, []);

  const handlePasswordKeyDown = useCallback((event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      // Программно отправляем форму — вызывает onSubmit формы
      event.currentTarget.form?.requestSubmit();
    }
  }, []);

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-[#f8f9fa]/95 px-5 py-8 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="login-title"
    >
      <div className="w-full max-w-[420px] overflow-hidden rounded-[28px] border border-white/70 bg-white/95 shadow-[0_24px_70px_rgba(26,28,30,0.18)]">
        <div className="border-b border-[#f0f0f0] px-6 pb-4 pt-5 sm:px-7">
          <p className="text-[10px] font-bold uppercase tracking-wider text-[#74777F]">
            {APP_BRAND.subtitle}
          </p>
          <h2 className="mt-1 text-xl font-bold text-[#1A1C1E]">{APP_BRAND.title}</h2>
          <p id="login-title" className="mt-2 text-sm font-medium text-[#5F6368]">
            {AUTH_COPY.title}
          </p>
        </div>

        <form className="space-y-4 px-6 pb-6 pt-5 sm:px-7" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label className="text-xs font-semibold text-[#5F6368]" htmlFor="workerCode">
              {AUTH_COPY.workerCodeLabel}
            </label>
            <input
              id="workerCode"
              name="workerCode"
              type="text"
              autoComplete="username"
              inputMode="text"
              value={workerCode}
              onChange={handleWorkerCodeChange}
              onKeyDown={handleWorkerCodeKeyDown}
              aria-invalid={Boolean(fieldErrors.workerCode)}
              aria-describedby={fieldErrors.workerCode ? 'workerCode-error' : undefined}
              placeholder={AUTH_COPY.workerCodePlaceholder}
              className={`w-full rounded-2xl border px-4 py-3 text-sm font-medium text-[#1A1C1E] outline-none transition ${
                fieldErrors.workerCode
                  ? 'border-[#e5a3a8] bg-[#fff6f7]'
                  : 'border-[#e7e8ea] bg-white focus:border-[#8fb3ff] focus:ring-2 focus:ring-[#e8f0ff]'
              }`}
            />
            {fieldErrors.workerCode && (
              <p id="workerCode-error" className="text-[11px] font-semibold text-[#9f3138]">
                {fieldErrors.workerCode}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-[#5F6368]" htmlFor="password">
              {AUTH_COPY.passwordLabel}
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              ref={passwordRef}
              onChange={handlePasswordChange}
              onKeyDown={handlePasswordKeyDown}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              placeholder={AUTH_COPY.passwordPlaceholder}
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
            {submitting ? AUTH_COPY.submitting : AUTH_COPY.submit}
          </button>

          <div className="text-center">
            <button
              type="button"
              onClick={() => setShowHint((prev) => !prev)}
              className="text-xs font-semibold text-[#1A1C1E] underline decoration-dotted underline-offset-4"
            >
              {AUTH_COPY.forgot}
            </button>
            {showHint && (
              <p className="mt-2 text-[11px] font-medium text-[#5F6368]">{AUTH_COPY.forgotHint}</p>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
