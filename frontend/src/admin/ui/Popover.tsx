import { useLayoutEffect, useState, type ReactNode, type RefObject } from 'react';
import { createPortal } from 'react-dom';

const VIEWPORT_MARGIN = 8;

interface PopoverProps {
  open: boolean;
  onClose: () => void;
  /** Элемент-якорь; попап позиционируется под его левым краем. */
  anchorRef: RefObject<HTMLElement | null>;
  /** Ширина попапа в px (по умолчанию 256 = w-64). */
  width?: number;
  children: ReactNode;
}

/**
 * Выпадающий попап через портал в document.body с фиксированным
 * позиционированием относительно якоря.
 *
 * Решает две архитектурные проблемы выпадающих фильтров в таблицах:
 *  - попап не является частью скролл-контейнера таблицы, поэтому никогда
 *    не растягивает его и не создаёт горизонтальную прокрутку;
 *  - позиция прижимается к краям viewport — попап не уезжает за экран
 *    даже у крайних правых колонок.
 *
 * Позиция пересчитывается при скролле (в любом контейнере) и resize.
 */
export function Popover({ open, onClose, anchorRef, width = 256, children }: PopoverProps) {
  const [pos, setPos] = useState<{ left: number; top: number; maxHeight: number } | null>(null);

  useLayoutEffect(() => {
    if (!open) return;

    const update = () => {
      const anchor = anchorRef.current;
      if (!anchor) return;
      const rect = anchor.getBoundingClientRect();
      const top = rect.bottom + 4;
      setPos({
        left: Math.max(
          VIEWPORT_MARGIN,
          Math.min(rect.left, window.innerWidth - width - VIEWPORT_MARGIN)
        ),
        top,
        maxHeight: Math.max(160, window.innerHeight - top - VIEWPORT_MARGIN),
      });
    };

    update();
    // capture: ловим скролл вложенных скролл-контейнеров (тело таблицы и т.п.)
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  }, [open, anchorRef, width]);

  if (!open) return null;

  return createPortal(
    <>
      <button
        type="button"
        aria-label="Закрыть фильтр"
        onClick={onClose}
        className="fixed inset-0 z-20 cursor-default bg-transparent"
      />
      <div
        role="dialog"
        className="fixed z-30 overflow-y-auto rounded-[16px] border border-[#e8eaed] bg-white p-3 normal-case shadow-[0_8px_24px_rgba(26,28,30,0.12)]"
        style={{
          left: pos?.left ?? VIEWPORT_MARGIN,
          top: pos?.top ?? 0,
          width,
          maxHeight: pos?.maxHeight,
          visibility: pos ? 'visible' : 'hidden',
        }}
      >
        {children}
      </div>
    </>,
    document.body
  );
}
