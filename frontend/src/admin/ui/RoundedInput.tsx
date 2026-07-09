import { useState, type InputHTMLAttributes, type ReactNode } from 'react';
import { IconEye, IconEyeOff } from './icons';

interface RoundedInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode;
  error?: string;
  hint?: string;
}

export function RoundedInput({
  label,
  error,
  hint,
  className = '',
  type = 'text',
  disabled,
  ...props
}: RoundedInputProps) {
  const [showPassword, setShowPassword] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword ? (showPassword ? 'text' : 'password') : type;

  return (
    <div className={`w-full ${className}`}>
      {label && (
        <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
          {label}
        </label>
      )}
      <div className="relative">
        <input
          type={inputType}
          disabled={disabled}
          className={
            'h-12 w-full rounded-[14px] border-[1.5px] bg-white px-4 text-[15px] text-[#1a1c1e] ' +
            'outline-none transition-all duration-200 ' +
            (error
              ? 'border-[#ea4335] focus:border-[#ea4335] focus:shadow-[0_0_0_3px_rgba(234,67,53,0.15)] '
              : 'border-[#e8eaed] focus:border-[#4285f4] focus:shadow-[0_0_0_3px_rgba(66,133,244,0.15)] ') +
            (disabled ? 'cursor-not-allowed bg-[#f8f9fa] text-[#74777f] ' : ' ') +
            (isPassword ? 'pr-11 ' : ' ')
          }
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            tabIndex={-1}
            onClick={() => setShowPassword((v) => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-[#74777f] transition-colors hover:text-[#1a1c1e]"
            aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
          >
            {showPassword ? <IconEyeOff size={20} /> : <IconEye size={20} />}
          </button>
        )}
      </div>
      {error && <p className="mt-1.5 text-xs text-[#ea4335]">{error}</p>}
      {hint && !error && <p className="mt-1.5 text-xs text-[#74777f]">{hint}</p>}
    </div>
  );
}
