import { useEffect, type ReactNode } from 'react';

interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: ReactNode;
  children: ReactNode;
}

export function BottomSheet({ isOpen, onClose, title, children }: BottomSheetProps) {
  useEffect(() => {
    if (!isOpen) return;

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex flex-col justify-end">
      <div
        className="absolute inset-0 bg-black/30 transition-opacity"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="relative w-full max-w-full animate-[slideUp_0.2s_ease-out] rounded-t-[24px] bg-white px-4 pb-6 pt-3 shadow-[0_-4px_24px_rgba(0,0,0,0.1)]"
        role="dialog"
        aria-modal="true"
      >
        <div className="mx-auto mb-4 h-1 w-9 rounded-full bg-[#e8eaed]" />
        {title && <h3 className="mb-4 text-lg font-bold text-[#1a1c1e]">{title}</h3>}
        {children}
      </div>
      <style>{`
        @keyframes slideUp {
          from { transform: translateY(100%); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
      `}</style>
    </div>
  );
}
