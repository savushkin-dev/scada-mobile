import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  subtitle?: string;
}

export function EmptyState({ icon, title, subtitle }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      {icon && (
        <div className="mb-4 flex h-20 w-20 items-center justify-center rounded-[20px] bg-[#f8f9fa] text-[#e8eaed]">
          {icon}
        </div>
      )}
      <p className="text-base font-medium text-[#74777f]">{title}</p>
      {subtitle && <p className="mt-1 text-sm text-[#74777f]">{subtitle}</p>}
    </div>
  );
}
