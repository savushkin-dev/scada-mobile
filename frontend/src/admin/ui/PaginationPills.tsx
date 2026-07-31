import { useRef } from 'react';
import { IconChevronLeft, IconChevronRight } from './icons';

interface PaginationPillsProps {
  page: number;
  perPage: number;
  total: number;
  onPageChange: (page: number) => void;
}

/**
 * Номера страниц с многоточием, как в референсе: «‹ 1 2 3 … 5 ›».
 * При небольшом числе страниц показываем все, иначе — первую, последнюю
 * и окно вокруг текущей.
 */
function getPageItems(current: number, totalPages: number): (number | 'ellipsis')[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }

  const windowPages = new Set<number>([1, totalPages, current - 1, current, current + 1]);
  const sorted = [...windowPages].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b);

  const items: (number | 'ellipsis')[] = [];
  let prev = 0;
  for (const p of sorted) {
    if (p - prev > 1) items.push('ellipsis');
    items.push(p);
    prev = p;
  }
  return items;
}

export function PaginationPills({ page, perPage, total, onPageChange }: PaginationPillsProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  if (!total || total <= 0) return null;

  const totalPages = Math.max(1, Math.ceil(total / perPage));
  const start = (page - 1) * perPage + 1;
  const end = Math.min(page * perPage, total);

  const pageItems = getPageItems(page, totalPages);

  const handlePageChange = (nextPage: number) => {
    if (nextPage === page || nextPage < 1 || nextPage > totalPages) return;
    onPageChange(nextPage);
    requestAnimationFrame(() => {
      containerRef.current?.scrollIntoView({ block: 'end', behavior: 'auto' });
    });
  };

  return (
    <div
      ref={containerRef}
      className="mt-2 flex flex-col items-center gap-3 border-t border-[#f0f0f0] pt-4 sm:flex-row sm:justify-between"
    >
      <span className="text-sm text-[#74777f]">
        Показано {start}–{end} из {total}
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

        {pageItems.map((item, index) =>
          item === 'ellipsis' ? (
            <span
              key={`ellipsis-${index}`}
              className="flex h-9 min-w-[36px] items-center justify-center px-2 text-[13px] font-semibold text-[#74777f]"
              aria-hidden
            >
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              onClick={() => handlePageChange(item)}
              aria-current={item === page ? 'page' : undefined}
              className={
                'flex h-9 min-w-[36px] items-center justify-center rounded-[12px] px-2 text-[13px] font-semibold transition-colors ' +
                (item === page
                  ? 'bg-[#4285f4] text-white '
                  : 'border border-[#e8eaed] bg-white text-[#1a1c1e] hover:bg-[#f8f9fa] ')
              }
            >
              {item}
            </button>
          )
        )}

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
