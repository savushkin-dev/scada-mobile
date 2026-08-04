import { useState, useRef, useCallback, useEffect, type ReactNode } from 'react';
import { AdminIcon } from './icons';

interface ResizablePanelsProps {
  left: ReactNode;
  right: ReactNode;
  /** Начальная ширина левой панели в процентах (по умолчанию 40). */
  defaultLeftWidth?: number;
  /** Минимальная ширина левой панели в процентах. */
  minLeftWidth?: number;
  /** Максимальная ширина левой панели в процентах. */
  maxLeftWidth?: number;
}

export function ResizablePanels({
  left,
  right,
  defaultLeftWidth = 40,
  minLeftWidth = 25,
  maxLeftWidth = 75,
}: ResizablePanelsProps) {
  const [leftWidth, setLeftWidth] = useState(defaultLeftWidth);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleMouseDown = useCallback(() => setIsDragging(true), []);

  useEffect(() => {
    if (!isDragging) return;

    const handleMouseMove = (e: MouseEvent) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const percent = (x / rect.width) * 100;
      setLeftWidth(Math.max(minLeftWidth, Math.min(maxLeftWidth, percent)));
    };

    const handleMouseUp = () => setIsDragging(false);

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isDragging, minLeftWidth, maxLeftWidth]);

  return (
    <div ref={containerRef} className="flex min-h-0 flex-1">
      <div
        className="flex min-h-0 flex-col"
        style={{ flex: `0 0 ${leftWidth}%`, maxWidth: `${leftWidth}%` }}
      >
        {left}
      </div>
      <div
        className="flex shrink-0 cursor-col-resize items-center justify-center px-1 select-none"
        onMouseDown={handleMouseDown}
        role="separator"
        aria-orientation="vertical"
        aria-label="Изменить ширину панелей"
      >
        <div className="flex h-8 w-5 items-center justify-center rounded-full bg-[#e8eaed] text-[#74777f] transition-colors hover:bg-[#d9dce1]">
          <AdminIcon name="resize-horizontal" size={10} />
        </div>
      </div>
      <div className="flex min-h-0 flex-1 flex-col">{right}</div>
    </div>
  );
}
