import { useCallback, useEffect, useRef } from 'react';

/**
 * Enables Enter-key navigation inside a form container:
 * - Enter on a field focuses the next focusable field.
 * - Enter on the last field submits the form via `onSubmit`.
 *
 * The callback is stored in a ref so the keydown listener does not need
 * to be re-attached on every render.
 */
export function useFormKeyboardNavigation(onSubmit: () => void) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onSubmitRef = useRef(onSubmit);

  useEffect(() => {
    onSubmitRef.current = onSubmit;
  }, [onSubmit]);

  const focusNextOrSubmit = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;

    const focusable = Array.from(
      container.querySelectorAll(
        'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])'
      )
    ) as HTMLElement[];

    const activeIndex = focusable.findIndex((el) => el === document.activeElement);
    if (activeIndex === -1) return;

    if (activeIndex < focusable.length - 1) {
      focusable[activeIndex + 1].focus();
    } else {
      onSubmitRef.current();
    }
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Enter') return;
      const target = e.target as HTMLElement;
      if (!['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)) return;
      if (target.tagName === 'TEXTAREA' && e.shiftKey) {
        // Allow Shift+Enter for newlines in textareas; plain Enter navigates/submits.
        return;
      }
      e.preventDefault();
      focusNextOrSubmit();
    };

    container.addEventListener('keydown', handleKeyDown);
    return () => container.removeEventListener('keydown', handleKeyDown);
  }, [focusNextOrSubmit]);

  return containerRef;
}
