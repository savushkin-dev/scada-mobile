import { useEffect, useRef, useState, type ReactNode } from 'react';
import { PillButton } from './PillButton';
import { IconChevronDown, IconDotsVertical, IconTrash } from './icons';

interface ActionMenuItem {
  key: string;
  label: string;
  variant?: 'default' | 'danger';
  /** Иконка пункта меню (для danger по умолчанию — корзина). */
  icon?: ReactNode;
  onClick: () => void;
}

interface ActionMenuProps {
  items: ActionMenuItem[];
  label?: string;
  icon?: ReactNode;
  align?: 'left' | 'right';
}

export function ActionMenu({
  items,
  label = 'Доп. параметры',
  icon = <IconChevronDown size={16} />,
  align = 'right',
}: ActionMenuProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  return (
    <div ref={containerRef} className="relative">
      <PillButton
        variant="secondary"
        icon={icon}
        onClick={() => setIsOpen((v) => !v)}
        className="h-9 px-4"
      >
        {label}
      </PillButton>
      {isOpen && (
        <div
          className={
            'absolute top-full z-30 mt-1 w-48 rounded-[14px] border border-[#e8eaed] bg-white py-1 shadow-[0_4px_16px_rgba(0,0,0,0.08)] ' +
            (align === 'right' ? 'right-0' : 'left-0')
          }
        >
          {items.map((item, index) => (
            <button
              key={item.key}
              type="button"
              onClick={() => {
                setIsOpen(false);
                item.onClick();
              }}
              className={
                'flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm font-medium transition-colors ' +
                (item.variant === 'danger'
                  ? 'text-[#ea4335] hover:bg-[#fff0f1] ' +
                    (index > 0 ? 'mt-1 border-t border-[#f0f0f0]' : '')
                  : 'text-[#1a1c1e] hover:bg-[#f8f9fa]')
              }
            >
              {item.icon ?? (item.variant === 'danger' ? <IconTrash size={16} /> : null)}
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export { IconDotsVertical };
export type { ActionMenuItem };
