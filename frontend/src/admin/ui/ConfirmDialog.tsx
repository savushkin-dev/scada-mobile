import { BottomSheet } from './BottomSheet';
import { PillButton } from './PillButton';
import { IconAlertCircle, IconTrash } from './icons';

interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title?: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  isMobile?: boolean;
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title = 'Подтвердите действие',
  message = 'Вы уверены?',
  confirmText = 'Удалить',
  cancelText = 'Отмена',
  isMobile = false,
}: ConfirmDialogProps) {
  const actions = (
    <div className="flex flex-col gap-2 sm:flex-row-reverse">
      <PillButton
        variant="danger"
        icon={<IconTrash size={18} />}
        onClick={onConfirm}
        fullWidth={isMobile}
      >
        {confirmText}
      </PillButton>
      <PillButton variant="secondary" onClick={onClose} fullWidth={isMobile}>
        {cancelText}
      </PillButton>
    </div>
  );

  if (isMobile) {
    return (
      <BottomSheet isOpen={isOpen} onClose={onClose} title={title}>
        <p className="mb-5 text-sm text-[#74777f]">{message}</p>
        {actions}
      </BottomSheet>
    );
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} aria-hidden="true" />
      <div
        className="relative w-full max-w-md animate-[scaleIn_0.15s_ease-out] rounded-[24px] bg-white p-6 shadow-[0_8px_32px_rgba(0,0,0,0.12)]"
        role="alertdialog"
        aria-modal="true"
      >
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#fceae9] text-[#ea4335]">
            <IconAlertCircle size={20} />
          </div>
          <h3 className="text-lg font-bold text-[#1a1c1e]">{title}</h3>
        </div>
        <p className="mb-6 text-sm text-[#74777f]">{message}</p>
        {actions}
      </div>
      <style>{`
        @keyframes scaleIn {
          from { transform: scale(0.95); opacity: 0; }
          to { transform: scale(1); opacity: 1; }
        }
      `}</style>
    </div>
  );
}
