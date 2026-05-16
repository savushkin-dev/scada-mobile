import { useCallback, useEffect, useRef, useState } from 'react';
import {
  API_BASE,
  FAB_ICON_STYLE,
  getFabButtonStyle,
  getFabLabelStyle,
  UI_BEHAVIOR,
  UI_COPY,
} from '../config';
import type { NotificationData } from '../types';
import { useAuth } from '../context/AuthContext';
import { apiFetch } from '../api/client';

/**
 * FAB для action "последняя партия" / toggle notification на детальной странице аппарата.
 *
 * Источники правды:
 * - визуальные константы и копирайт: {@link ../config/ui.ts}, {@link ../config/styles.ts};
 * - runtime-порог сворачивания: {@link ../config/runtime.ts}.
 *
 * POST /api/line/{unitId}/last-batch → toggle notification (activate / deactivate).
 * Заголовок X-User-Id передаётся для идентификации работника.
 */

interface Props {
  visible: boolean;
  unitId: string | null;
  scrollContainer: HTMLElement | null;
  /** Активное уведомление для данного аппарата (из AppContext), или null. */
  notification: NotificationData | null;
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function getViewportWidth(): number {
  if (typeof window === 'undefined') return 360;
  const visualViewportWidth = window.visualViewport?.width ?? 0;
  return Math.max(window.innerWidth, visualViewportWidth, document.documentElement.clientWidth);
}

export function Fab({ visible, unitId, scrollContainer, notification }: Props) {
  const { userId } = useAuth();
  const [collapseProgress, setCollapseProgress] = useState(0);
  const [viewportWidth, setViewportWidth] = useState(() => getViewportWidth());
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [toggleResult, setToggleResult] = useState<
    'idle' | 'activated' | 'deactivated' | 'already_active'
  >('idle');
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
      const resp = await apiFetch(`${API_BASE}/api/v1.0.0/line/${unitId}/last-batch`, {
        method: 'POST',
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const body = await resp.json();
      setToggleResult(body.status ?? 'idle');
    } catch (e) {
      console.warn('[FAB] last-batch fallback:', (e as Error).message);
      setToggleResult('idle');
    }
    setSent(true);
    setSending(false);
    setTimeout(() => {
      setSent(false);
      setToggleResult('idle');
    }, UI_BEHAVIOR.fabSentResetDelayMs);
  }

  if (!visible) return null;

  const isActiveByMe = notification != null && userId != null && notification.creatorId === userId;
  const isActiveByOther =
    notification != null && userId != null && notification.creatorId !== userId;

  // Визуальное состояние кнопки после toggle
  const showToggleFeedback = sent && toggleResult !== 'idle';

  // Определяем label и icon
  let icon: string;
  let label: string;
  if (showToggleFeedback) {
    icon = toggleResult === 'activated' ? '🔔' : toggleResult === 'deactivated' ? '🔕' : '⏳';
    label =
      toggleResult === 'activated'
        ? 'Уведомление создано!'
        : toggleResult === 'deactivated'
          ? 'Уведомление снято!'
          : `Активно от ${notification?.creatorId ?? '?'}`;
  } else if (isActiveByOther) {
    icon = '⏳';
    label = `Уведомление от ${notification!.creatorId}`;
  } else if (isActiveByMe) {
    icon = '🔕';
    label = 'Снять уведомление';
  } else {
    icon = UI_COPY.fabDefaultIcon;
    label = UI_COPY.fabActionLabel;
  }

  const buttonStyle = getFabButtonStyle(collapseProgress, sent, viewportWidth);
  const labelStyle = getFabLabelStyle(collapseProgress, viewportWidth);

  return (
    <button
      aria-label={UI_COPY.fabAriaLabel}
      disabled={sending || isActiveByOther}
      onClick={handleClick}
      style={buttonStyle}
    >
      <span style={FAB_ICON_STYLE}>
        {sent ? (showToggleFeedback ? icon : UI_COPY.fabSentIcon) : icon}
      </span>
      <span style={labelStyle}>
        {sent ? (showToggleFeedback ? label : UI_COPY.fabSentLabel) : label}
      </span>
    </button>
  );
}
