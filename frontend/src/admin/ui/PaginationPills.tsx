import { useRef } from 'react';
import { IconChevronLeft, IconChevronRight } from './icons';

interface PaginationPillsProps {
  page: number;
  perPage: number;
  total: number;
  onPageChange: (page: number) => void;
}

export function PaginationPills({ page, perPage, total, onPageChange }: PaginationPillsProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  if (total <= perPage) return null;

  const totalPages = Math.ceil(total / perPage);
  const start = (page - 1) * perPage + 1;
  const end = Math.min(page * perPage, total);

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1);

  const handlePageChange = (nextPage: number) => {
    onPageChange(nextPage);
    requestAnimationFrame(() => {
      containerRef.current?.scrollIntoView({ block: 'end', behavior: 'auto' });
    });
  };

  return (
    <div
      ref={containerRef}
      className="flex flex-col items-center gap-3 pt-4 sm:flex-row sm:justify-between"
    >
      <span className="text-sm text-[#74777f]">
        {start}-{end} of {total}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => handlePageChange(page - 1)}
          className="flex h-9 w-9 items-center justify-center rounded-[12px] border border-[#e8eaed] bg-white text-[#1a1c1e] transition-colors hover:bg-[#f8f9fa] disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Предыдущая страница"
        >
          <IconChevronLeft size={16} />
        </button>

        {pages.map((p) => (
          <button
            key={p}
            type="button"
            onClick={() => handlePageChange(p)}
            className={
              'flex h-9 min-w-[36px] items-center justify-center rounded-[12px] px-2 text-[13px] font-semibold transition-colors ' +
              (p === page
                ? 'bg-[#4285f4] text-white '
                : 'border border-[#e8eaed] bg-white text-[#1a1c1e] hover:bg-[#f8f9fa] ')
            }
          >
            {p}
          </button>
        ))}

        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => handlePageChange(page + 1)}
          className="flex h-9 w-9 items-center justify-center rounded-[12px] border border-[#e8eaed] bg-white text-[#1a1c1e] transition-colors hover:bg-[#f8f9fa] disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Следующая страница"
        >
          <IconChevronRight size={16} />
        </button>
      </div>
    </div>
  );
}
