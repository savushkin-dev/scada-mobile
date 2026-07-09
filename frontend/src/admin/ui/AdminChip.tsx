import type { ReactNode } from 'react';
import { IconX } from './icons';

interface AdminChipProps {
  children: ReactNode;
  onRemove?: () => void;
  className?: string;
}

export function AdminChip({ children, onRemove, className = '' }: AdminChipProps) {
  return (
    <span
      className={
        'inline-flex max-w-full items-center gap-1 rounded-[16px] bg-[#edeef0] px-2.5 py-1 text-[13px] font-medium text-[#1a1c1e] ' +
        className
      }
    >
      <span className="truncate">{children}</span>
      {onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
          className="ml-0.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-[#5f6368] transition-colors hover:bg-[#dadce0] hover:text-[#1a1c1e]"
          aria-label="Удалить"
        >
          <IconX size={12} />
        </button>
      )}
    </span>
  );
}
