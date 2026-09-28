import { useEffect, useRef, type ReactNode } from 'react';

interface Props {
  open: boolean;
  title: string;
  subtitle: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  /** Цвет кнопки подтверждения. По умолчанию — синий (primary). */
  confirmColor?: 'blue' | 'red';
  /** Дополнительный контент между subtitle и кнопками. */
  children?: ReactNode;
}

const CONFIRM_COLOR_CLASS: Record<NonNullable<Props['confirmColor']>, string> = {
  blue: 'bg-[#3B82F6] shadow-[0_4px_14px_rgba(59,130,246,0.35)]',
  red: 'bg-[#EA4335] shadow-[0_4px_14px_rgba(234,67,53,0.35)]',
};

/**
 * Универсальный overlay подтверждения действия.
 *
 * Визуальный стиль основан на overlay выхода из аккаунта (ProfilePage):
 * - затемнённый фон на весь экран с лёгким блюром;
 * - белая панель по центру с тяжёлым shadow;
 * - две кнопки: подтверждение (цветная) и отмена (серая/белая).
 */
export function ConfirmationOverlay({
  open,
  title,
  subtitle,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
  confirmColor = 'blue',
  children,
}: Props) {
  const confirmButtonRef = useRef<HTMLButtonElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const focusFrameRef = useRef<number | null>(null);

  useEffect(() => {
    if (!open) return;
    previousFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    focusFrameRef.current = requestAnimationFrame(() => confirmButtonRef.current?.focus());
    const handleKeyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCancel();
        return;
      }
      if (event.key !== 'Tab') return;
      const dialog = confirmButtonRef.current?.closest('[role="dialog"]');
      if (!dialog) return;
      const focusable = [
        ...dialog.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        ),
      ].filter((element) => !element.hasAttribute('disabled'));
      if (focusable.length === 0) return;
      const currentIndex = focusable.indexOf(document.activeElement as HTMLElement);
      const nextIndex = event.shiftKey
        ? (currentIndex - 1 + focusable.length) % focusable.length
        : (currentIndex + 1) % focusable.length;
      event.preventDefault();
      focusable[nextIndex].focus();
    };
    window.addEventListener('keydown', handleKeyboard);
    return () => {
      window.removeEventListener('keydown', handleKeyboard);
      if (focusFrameRef.current != null) cancelAnimationFrame(focusFrameRef.current);
      previousFocusRef.current?.focus();
    };
  }, [onCancel, open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-6 backdrop-blur-[2px]"
      onClick={onCancel}
      role="presentation"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="w-full max-w-[360px] rounded-[26px] bg-[#f8fafc] p-6 shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-base font-semibold text-[#1A1C1E]">{title}</h3>
        <p className="mt-2 text-sm text-[#5F6368]">{subtitle}</p>
        {children}
        <div className="mt-6 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-2xl border border-[#e2e8f0] bg-white px-5 py-2.5 text-sm font-semibold text-[#1A1C1E] transition active:scale-[0.98]"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            ref={confirmButtonRef}
            className={`rounded-2xl px-5 py-2.5 text-sm font-semibold text-white transition active:scale-[0.98] ${CONFIRM_COLOR_CLASS[confirmColor]}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
