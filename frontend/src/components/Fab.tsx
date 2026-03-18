import { useEffect, useRef, useState } from 'react';
import {
  API_BASE,
  FAB_ICON_STYLE,
  FAB_LABEL_STYLE,
  getFabButtonStyle,
  HTTP_REQUEST,
  UI_BEHAVIOR,
  UI_COPY,
} from '../config';

/**
 * FAB для action "последняя партия" на детальной странице аппарата.
 *
 * Источники правды:
 * - визуальные константы и копирайт: {@link ../config/ui.ts}, {@link ../config/styles.ts};
 * - runtime-порог сворачивания: {@link ../config/runtime.ts}.
 *
 * Компонент не хранит бизнес-стейт партии, только выполняет side-effect запроса.
 */

interface Props {
  visible: boolean;
  unitId: string | null;
  scrollContainer: HTMLElement | null;
}

export function Fab({ visible, unitId, scrollContainer }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const lastScrollY = useRef(0);

  useEffect(() => {
    if (!scrollContainer || !visible) return;
    const el = scrollContainer;

    function handleScroll() {
      const currentY = el.scrollTop;
      const delta = currentY - lastScrollY.current;
      // Простой UX-алгоритм: вниз -> свернуть; вверх -> развернуть.
      if (delta > UI_BEHAVIOR.fabCollapseScrollDeltaPx) setCollapsed(true);
      else if (delta < -UI_BEHAVIOR.fabCollapseScrollDeltaPx) setCollapsed(false);
      lastScrollY.current = currentY;
    }

    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [scrollContainer, visible]);

  useEffect(() => {
    if (!visible) {
      setCollapsed(false);
      lastScrollY.current = 0;
    }
  }, [visible]);

  async function handleClick() {
    if (!unitId || sending) return;
    setSending(true);
    try {
      const resp = await fetch(`${API_BASE}/api/line/${unitId}/last-batch`, {
        method: HTTP_REQUEST.post,
        headers: { 'Content-Type': HTTP_REQUEST.jsonContentType },
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    } catch (e) {
      console.warn('[FAB] last-batch fallback:', (e as Error).message);
    }
    setSent(true);
    setSending(false);
    setTimeout(() => setSent(false), UI_BEHAVIOR.fabSentResetDelayMs);
  }

  if (!visible) return null;

  return (
    <button
      aria-label={UI_COPY.fabAriaLabel}
      disabled={sending}
      onClick={handleClick}
      style={getFabButtonStyle(collapsed, sent)}
    >
      <span style={FAB_ICON_STYLE}>{sent ? UI_COPY.fabSentIcon : UI_COPY.fabDefaultIcon}</span>
      {!collapsed && (
        <span style={FAB_LABEL_STYLE}>{sent ? UI_COPY.fabSentLabel : UI_COPY.fabActionLabel}</span>
      )}
    </button>
  );
}
