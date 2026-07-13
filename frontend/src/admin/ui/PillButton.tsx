import type { ButtonHTMLAttributes, ReactNode } from 'react';

type PillVariant = 'primary' | 'danger' | 'secondary';

interface PillButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: PillVariant;
  icon?: ReactNode;
  fullWidth?: boolean;
}

const variantClasses: Record<PillVariant, string> = {
  primary:
    'bg-[#1a1c1e] text-white hover:bg-[#2b2f36] active:bg-[#0d0f11] shadow-[0_2px_8px_rgba(26,28,30,0.12)]',
  danger:
    'bg-[#ea4335] text-white border-[1.5px] border-[#ea4335] hover:bg-[#c13525] hover:border-[#c13525] active:bg-[#a62d1f] active:border-[#a62d1f]',
  secondary:
    'bg-[#f8f9fa] text-[#1a1c1e] border border-[#d1d5db] hover:bg-[#edeef0] active:bg-[#e8eaed]',
};

export function PillButton({
  children,
  variant = 'primary',
  icon,
  fullWidth = false,
  className = '',
  disabled,
  ...props
}: PillButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      className={
        'inline-flex h-11 items-center justify-center gap-2 rounded-[44px] px-5 text-sm font-semibold ' +
        'transition-all duration-200 ease-in-out disabled:cursor-not-allowed disabled:opacity-50 ' +
        'active:scale-[0.98] ' +
        variantClasses[variant] +
        (fullWidth ? ' w-full ' : ' ') +
        className
      }
      {...props}
    >
      {icon && <span className="shrink-0">{icon}</span>}
      {children}
    </button>
  );
}
