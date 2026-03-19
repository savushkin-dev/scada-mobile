import { useCallback, useEffect, useRef, useState } from 'react';
import {
  API_BASE,
  FAB_ICON_STYLE,
  getFabButtonStyle,
  getFabLabelStyle,
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

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function getViewportWidth(): number {
  if (typeof window === 'undefined') return 360;
  const visualViewportWidth = window.visualViewport?.width ?? 0;
  return Math.max(window.innerWidth, visualViewportWidth, document.documentElement.clientWidth);
}

export function Fab({ visible, unitId, scrollContainer }: Props) {
  const [collapseProgress, setCollapseProgress] = useState(0);
  const [viewportWidth, setViewportWidth] = useState(() => getViewportWidth());
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const lastScrollY = useRef(0);
  const targetCollapseProgressRef = useRef(0);
  const animatedCollapseProgressRef = useRef(0);
  const animationFrameRef = useRef<number | null>(null);

  const stopCollapseAnimation = useCallback(() => {
    if (animationFrameRef.current === null) return;
    cancelAnimationFrame(animationFrameRef.current);
    animationFrameRef.current = null;
  }, []);

  const startCollapseAnimation = useCallback(() => {
    if (animationFrameRef.current !== null) return;

    const step = () => {
      const current = animatedCollapseProgressRef.current;
      const target = targetCollapseProgressRef.current;
      const delta = target - current;

      if (Math.abs(delta) < 0.001) {
        animatedCollapseProgressRef.current = target;
        setCollapseProgress(target);
        animationFrameRef.current = null;
        return;
      }

      const next = current + delta * UI_BEHAVIOR.fabCollapseSmoothing;
      animatedCollapseProgressRef.current = next;
      setCollapseProgress(next);
      animationFrameRef.current = requestAnimationFrame(step);
    };

    animationFrameRef.current = requestAnimationFrame(step);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const handleResize = () => {
      setViewportWidth(getViewportWidth());
    };

    handleResize();
    window.addEventListener('resize', handleResize);
    window.visualViewport?.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      window.visualViewport?.removeEventListener('resize', handleResize);
    };
  }, []);

  useEffect(() => {
    if (!scrollContainer || !visible) return;
    const el = scrollContainer;
    lastScrollY.current = Math.max(0, el.scrollTop);

    function handleScroll() {
      const currentY = Math.max(0, el.scrollTop);
      const delta = currentY - lastScrollY.current;
      lastScrollY.current = currentY;

      if (Math.abs(delta) <= UI_BEHAVIOR.fabScrollNoiseThresholdPx) return;

      targetCollapseProgressRef.current = clamp01(
        targetCollapseProgressRef.current + delta / UI_BEHAVIOR.fabCollapseDistancePx
      );
      startCollapseAnimation();
    }

    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [scrollContainer, startCollapseAnimation, visible]);

  useEffect(() => {
    if (!visible) {
      stopCollapseAnimation();
      targetCollapseProgressRef.current = 0;
      animatedCollapseProgressRef.current = 0;
      setCollapseProgress(0);
      lastScrollY.current = 0;
      return;
    }

    if (scrollContainer) {
      lastScrollY.current = Math.max(0, scrollContainer.scrollTop);
    }
  }, [visible, scrollContainer, stopCollapseAnimation]);

  useEffect(() => {
    return () => {
      stopCollapseAnimation();
    };
  }, [stopCollapseAnimation]);

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

  const buttonStyle = getFabButtonStyle(collapseProgress, sent, viewportWidth);
  const labelStyle = getFabLabelStyle(collapseProgress, viewportWidth);

  return (
    <button
      aria-label={UI_COPY.fabAriaLabel}
      disabled={sending}
      onClick={handleClick}
      style={buttonStyle}
    >
      <span style={FAB_ICON_STYLE}>{sent ? UI_COPY.fabSentIcon : UI_COPY.fabDefaultIcon}</span>
      <span style={labelStyle}>{sent ? UI_COPY.fabSentLabel : UI_COPY.fabActionLabel}</span>
    </button>
  );
}
