import { useEffect, useRef, useState } from 'react';
import { ConfirmDialog } from './ConfirmDialog';
import { IconDotsVertical, IconPencil, IconTrash, IconPower, IconPowerOff } from './icons';

interface RowActionsMenuProps {
  onEdit?: () => void;
  isActive?: boolean;
  onToggleActive?: () => void;
  onDelete?: () => void;
}

export function RowActionsMenu({
  onEdit,
  isActive,
  onToggleActive,
  onDelete,
}: RowActionsMenuProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
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

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 1024);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  const hasToggle = isActive !== undefined && onToggleActive != null;
  const hasDelete = onDelete != null;

  const handleDelete = () => {
    setShowDeleteConfirm(false);
    onDelete?.();
  };

  return (
    <div ref={containerRef} className="relative ml-auto">
      <button
        type="button"
        onClick={() => setIsOpen((v) => !v)}
        aria-label="Дополнительные действия"
        className="flex h-9 w-9 items-center justify-center rounded-full text-[#74777f] transition-colors hover:bg-[#f0f7ff] hover:text-[#4285f4]"
      >
        <IconDotsVertical size={18} />
      </button>

      {isOpen && (
        <div className="absolute top-full right-0 z-30 mt-1 w-48 rounded-[14px] border border-[#e8eaed] bg-white py-1 shadow-[0_4px_16px_rgba(0,0,0,0.08)]">
          {onEdit && (
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                onEdit();
              }}
              className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm font-medium text-[#1a1c1e] transition-colors hover:bg-[#f8f9fa]"
            >
              <IconPencil size={16} />
              Изменить
            </button>
          )}

          {hasToggle && (
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                onToggleActive();
              }}
              className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm font-medium text-[#1a1c1e] transition-colors hover:bg-[#f8f9fa]"
            >
              {isActive ? <IconPowerOff size={16} /> : <IconPower size={16} />}
              {isActive ? 'Деактивировать' : 'Активировать'}
            </button>
          )}

          {hasDelete && (
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                setShowDeleteConfirm(true);
              }}
              className={`flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm font-medium text-[#ea4335] transition-colors hover:bg-[#fff0f1] ${onEdit || hasToggle ? 'mt-1 border-t border-[#f0f0f0]' : ''}`}
            >
              <IconTrash size={16} />
              Удалить
            </button>
          )}
        </div>
      )}

      {hasDelete && (
        <ConfirmDialog
          isOpen={showDeleteConfirm}
          onClose={() => setShowDeleteConfirm(false)}
          onConfirm={handleDelete}
          title="Удалить запись?"
          message="Это действие нельзя отменить."
          confirmText="Удалить"
          isMobile={isMobile}
        />
      )}
    </div>
  );
}
