import { useState } from 'react';
import { API_BASE, FAB_ICON_STYLE, getFabButtonStyle, UI_BEHAVIOR, UI_COPY } from '../config';
import type { NotificationData } from '../types';
import { useAuth } from '../context/AuthContext';
import { apiFetch } from '../api/client';
import { ConfirmationOverlay } from './ConfirmationOverlay';

/**
 * FAB для action "последняя партия" / toggle notification на детальной странице аппарата.
 *
 * Кнопка всегда компактная (круглая, только иконка): на экране терминала 4.2"
 * развёрнутая подпись перекрывала нижнюю часть контента и мешала прокрутке.
 * Текстовое состояние доступно через aria-label.
 *
 * Источники правды:
 * - визуальные константы и копирайт: {@link ../config/ui.ts}, {@link ../config/styles.ts};
 * - runtime-константы: {@link ../config/runtime.ts}.
 *
 * POST /api/line/{unitId}/last-batch → toggle notification (activate / deactivate).
 * Заголовок Authorization передаётся через apiFetch.
 */

interface Props {
  visible: boolean;
  unitId: string | null;
  /** Активное уведомление для данного аппарата (из AppContext), или null. */
  notification: NotificationData | null;
}

export function Fab({ visible, unitId, notification }: Props) {
  const { userId } = useAuth();
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [toggleResult, setToggleResult] = useState<
    'idle' | 'activated' | 'deactivated' | 'already_active'
  >('idle');
  const [overlayOpen, setOverlayOpen] = useState(false);

  async function handleConfirm() {
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

  /** CSS filter для перекраски bell.svg / bell-off.svg в белый цвет. */
  const BELL_WHITE_FILTER =
    'brightness(0) saturate(100%) invert(100%) sepia(0%) saturate(0%) hue-rotate(0deg) brightness(100%) contrast(100%)';

  // Определяем label (для aria-label) и icon
  let iconSrc: string | null;
  let label: string;
  if (showToggleFeedback) {
    iconSrc =
      toggleResult === 'activated'
        ? '/assets/bell.svg'
        : toggleResult === 'deactivated'
          ? '/assets/bell-off.svg'
          : null;
    label =
      toggleResult === 'activated'
        ? 'Уведомление создано!'
        : toggleResult === 'deactivated'
          ? 'Уведомление снято!'
          : `Активно от ${notification?.creatorId ?? '?'}`;
  } else if (isActiveByOther) {
    iconSrc = null;
    label = `Уведомление от ${notification!.creatorId}`;
  } else if (isActiveByMe) {
    iconSrc = '/assets/bell-off.svg';
    label = 'Снять уведомление';
  } else {
    iconSrc = '/assets/bell.svg';
    label = UI_COPY.fabActionLabel;
  }

  return (
    <>
      <button
        aria-label={sent ? (showToggleFeedback ? label : UI_COPY.fabSentLabel) : label}
        disabled={sending || isActiveByOther}
        onClick={() => setOverlayOpen(true)}
        style={getFabButtonStyle(sent)}
      >
        <span style={FAB_ICON_STYLE}>
          {sent ? (
            showToggleFeedback && iconSrc ? (
              <img
                src={iconSrc}
                alt=""
                aria-hidden="true"
                style={{ width: '1.15rem', height: '1.15rem', filter: BELL_WHITE_FILTER }}
              />
            ) : (
              UI_COPY.fabSentIcon
            )
          ) : iconSrc ? (
            <img
              src={iconSrc}
              alt=""
              aria-hidden="true"
              style={{ width: '1.15rem', height: '1.15rem', filter: BELL_WHITE_FILTER }}
            />
          ) : (
            '⏳'
          )}
        </span>
      </button>

      <ConfirmationOverlay
        open={overlayOpen}
        title={isActiveByMe ? 'Снять уведомление?' : 'Отправить последнюю партию?'}
        subtitle={
          isActiveByMe
            ? 'Уведомление о последней партии будет снято'
            : 'Данные будут направлены ответственным сотрудникам'
        }
        confirmLabel={isActiveByMe ? 'Снять' : 'Отправить'}
        cancelLabel="Отмена"
        onConfirm={() => {
          setOverlayOpen(false);
          void handleConfirm();
        }}
        onCancel={() => setOverlayOpen(false)}
        confirmColor="blue"
      />
    </>
  );
}
