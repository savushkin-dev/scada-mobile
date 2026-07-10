import type { ReactNode } from 'react';

interface AdminCardProps {
  children: ReactNode;
  className?: string;
  title?: ReactNode;
  subtitle?: ReactNode;
  icon?: ReactNode;
}

export function AdminCard({ children, className = '', title, subtitle, icon }: AdminCardProps) {
  return (
    <section className={'rounded-[20px] bg-white p-5 lg:rounded-[24px] lg:p-6 ' + className}>
      {(title || subtitle || icon) && (
        <div className="mb-4 flex items-start gap-3 border-b border-[#f0f0f0] pb-3 lg:mb-5">
          {icon && <div className="text-[#1a1c1e]">{icon}</div>}
          <div className="min-w-0 flex-1">
            {title && <h2 className="text-base font-bold text-[#1a1c1e] lg:text-lg">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-sm text-[#74777f]">{subtitle}</p>}
          </div>
        </div>
      )}
      {children}
    </section>
  );
}
