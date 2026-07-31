import { useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { createPortal } from 'react-dom';

const VIEWPORT_MARGIN = 8;
const GAP = 4;
const MIN_POPUP_HEIGHT = 160;

const DEFAULT_PANEL_CLASSNAME =
  'rounded-[16px] border border-[#e8eaed] bg-white p-3 normal-case shadow-[0_8px_24px_rgba(26,28,30,0.12)]';

interface PopoverProps {
  open: boolean;
  onClose: () => void;
  /** Элемент-якорь; попап позиционируется относительно него. */
  anchorRef: RefObject<HTMLElement | null>;
  /** Ширина попапа в px (по умолчанию 256 = w-64). */
  width?: number;
  /** К какому краю якоря прижимать попап по горизонтали. */
  align?: 'left' | 'right';
  /** Оформление панели; по умолчанию — стиль выпадающих фильтров. */
  panelClassName?: string;
  /** aria-label полноэкранной подложки, закрывающей попап кликом снаружи. */
  closeLabel?: string;
  children: ReactNode;
}

/**
 * Выпадающий попап через портал в document.body с фиксированным
 * позиционированием относительно якоря.
 *
 * Решает три архитектурные проблемы выпадающих элементов в таблицах:
 *  - попап не является частью скролл-контейнера таблицы, поэтому никогда
 *    не растягивает его и не создаёт горизонтальную прокрутку;
 *  - позиция прижимается к краям viewport — попап не уезжает за экран
 *    даже у крайних правых колонок;
 *  - если под якорем не хватает места, попап открывается вверх (flip);
 *    решение принимается по измеренной высоте контента, а не по оценке.
 *
 * Позиция пересчитывается при скролле (в любом контейнере) и resize.
 */
export function Popover({
  open,
  onClose,
  anchorRef,
  width = 256,
  align = 'left',
  panelClassName,
  closeLabel = 'Закрыть',
  children,
}: PopoverProps) {
  const [pos, setPos] = useState<{ left: number; top: number; maxHeight: number } | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!open) return;

    const update = () => {
      const anchor = anchorRef.current;
      if (!anchor) return;
      const rect = anchor.getBoundingClientRect();
      const contentHeight = panelRef.current?.offsetHeight ?? 0;
      const spaceBelow = window.innerHeight - rect.bottom - GAP - VIEWPORT_MARGIN;
      const spaceAbove = rect.top - GAP - VIEWPORT_MARGIN;
      // Flip: если снизу не помещаемся и сверху места больше — открываем вверх.
      const openUp = contentHeight > spaceBelow && spaceAbove > spaceBelow;
      const available = openUp ? spaceAbove : spaceBelow;
      setPos({
        left: Math.max(
          VIEWPORT_MARGIN,
          Math.min(
            align === 'right' ? rect.right - width : rect.left,
            window.innerWidth - width - VIEWPORT_MARGIN
          )
        ),
        top: openUp
          ? Math.max(VIEWPORT_MARGIN, rect.top - GAP - Math.min(contentHeight, available))
          : rect.bottom + GAP,
        maxHeight: Math.max(MIN_POPUP_HEIGHT, available),
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
  }, [open, anchorRef, width, align]);

  if (!open) return null;

  return createPortal(
    <>
      <button
        type="button"
        aria-label={closeLabel}
        onClick={onClose}
        className="fixed inset-0 z-20 cursor-default bg-transparent"
      />
      <div
        ref={panelRef}
        role="dialog"
        className={`fixed z-30 overflow-y-auto ${panelClassName ?? DEFAULT_PANEL_CLASSNAME}`}
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
