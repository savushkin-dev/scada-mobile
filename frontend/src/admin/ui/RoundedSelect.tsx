import type { ReactNode, SelectHTMLAttributes } from 'react';

interface RoundedSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: ReactNode;
  error?: string;
  hint?: string;
}

export function RoundedSelect({
  label,
  error,
  hint,
  className = '',
  children,
  disabled,
  ...props
}: RoundedSelectProps) {
  return (
    <div className={`w-full ${className}`}>
      {label && (
        <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.05em] text-[#74777f]">
          {label}
        </label>
      )}
      <div className="relative">
        <select
          disabled={disabled}
          className={
            'h-12 w-full appearance-none rounded-[14px] border-[1.5px] bg-white px-4 pr-10 text-[15px] text-[#1a1c1e] ' +
            'outline-none transition-all duration-200 ' +
            (error
              ? 'border-[#ea4335] focus:border-[#ea4335] focus:shadow-[0_0_0_3px_rgba(234,67,53,0.15)] '
              : 'border-[#e8eaed] focus:border-[#4285f4] focus:shadow-[0_0_0_3px_rgba(66,133,244,0.15)] ') +
            (disabled ? 'cursor-not-allowed bg-[#f8f9fa] text-[#74777f] ' : ' ')
          }
          {...props}
        >
          {children}
        </select>
        <svg
          className="pointer-events-none absolute right-3 top-1/2 h-5 w-5 -translate-y-1/2 text-[#74777f]"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
      {error && <p className="mt-1.5 text-xs text-[#ea4335]">{error}</p>}
      {hint && !error && <p className="mt-1.5 text-xs text-[#74777f]">{hint}</p>}
    </div>
  );
}
