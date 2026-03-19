import type { CSSProperties } from 'react';
import { UI_ANIMATION, UI_BEHAVIOR } from './runtime';
import { UI_PALETTE } from './ui';

/**
 * Централизованные inline-style константы.
 *
 * Назначение файла — дать единый источник правды для style-объектов,
 * используемых в TSX-компонентах, где utility-классов недостаточно.
 *
 * Общие дизайн-токены (цвета, копирайт, табы) находятся в {@link ./ui.ts}.
 */

export const PAGE_FADE_SECTION_STYLE: CSSProperties = {
  flex: 1,
  overflowY: 'auto',
  width: '100%',
  display: 'flex',
  flexDirection: 'column',
  animation: UI_ANIMATION.fadeInDefault,
};

export const DETAILS_PAGE_STYLE: CSSProperties = {
  flex: 1,
  overflow: 'hidden',
  animation: UI_ANIMATION.fadeInDefault,
};

export const BACK_BUTTON_STYLE: CSSProperties = {
  width: '40px',
  height: '40px',
  borderRadius: '50%',
  border: 'none',
  background: UI_PALETTE.softBlue,
  cursor: 'pointer',
  fontSize: '1.1rem',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
};

export const DETAILS_SCROLL_SECTION_STYLE: CSSProperties = {
  flex: 1,
  overflowY: 'auto',
  padding: '0 16px',
  paddingBottom: `calc(${UI_BEHAVIOR.detailsBottomPaddingPx ?? 80}px + var(--bottom-safe-offset, 0px))`,
};

export const CARD_TITLE_BETWEEN_STYLE: CSSProperties = {
  justifyContent: 'space-between',
};

export const BATCH_EXPANDED_SECTION_STYLE: CSSProperties = {
  paddingTop: '12px',
  animation: UI_ANIMATION.fadeInFast,
};

export const QUEUE_PRIMARY_TEXT_STYLE: CSSProperties = {
  fontWeight: 600,
  fontSize: '0.95rem',
  color: UI_PALETTE.brandText,
};

export const QUEUE_SECONDARY_TEXT_STYLE: CSSProperties = {
  fontSize: '0.8rem',
  color: UI_PALETTE.mutedText,
  marginTop: '4px',
};

export const LOGS_ACTIVE_TITLE_STYLE: CSSProperties = {
  color: UI_PALETTE.critical,
};

export const LOGS_EMPTY_SUCCESS_STYLE: CSSProperties = {
  textAlign: 'center',
  color: UI_PALETTE.success,
  padding: '16px 0',
  fontWeight: 600,
  fontSize: '0.9rem',
};

export const LOGS_ERROR_NAME_STYLE: CSSProperties = {
  fontWeight: 700,
  color: UI_PALETTE.critical,
  fontSize: '0.9rem',
};

export const LOGS_ERROR_DESC_STYLE: CSSProperties = {
  fontSize: '0.85rem',
  color: UI_PALETTE.brandText,
  marginTop: '4px',
};

export const LOGS_META_STYLE: CSSProperties = {
  fontSize: '0.75rem',
  color: UI_PALETTE.mutedText,
  marginBottom: '4px',
};

export const LOGS_GROUP_BADGE_STYLE: CSSProperties = {
  background: UI_PALETTE.neutralSurface,
  color: UI_PALETTE.neutralText,
  padding: '2px 7px',
  borderRadius: '8px',
  marginLeft: '6px',
  fontWeight: 600,
  fontSize: '0.7rem',
};

export const LOGS_DESCRIPTION_STYLE: CSSProperties = {
  fontSize: '0.9rem',
  color: UI_PALETTE.brandText,
};

export const FAB_ICON_STYLE: CSSProperties = {
  fontSize: '1.15rem',
  flexShrink: 0,
  lineHeight: 1,
};

export const FAB_LABEL_STYLE: CSSProperties = {
  overflow: 'hidden',
  maxWidth: 'min(160px, calc(100vw - 140px))',
  opacity: 1,
  marginLeft: '8px',
  textOverflow: 'ellipsis',
};

export const ERROR_FALLBACK_CONTAINER_STYLE: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  minHeight: '100dvh',
  padding: '24px',
  background: 'var(--clr-bg-app, #f8f9fa)',
  textAlign: 'center',
  gap: '16px',
};

export const ERROR_FALLBACK_ICON_STYLE: CSSProperties = {
  width: '56px',
  height: '56px',
  borderRadius: '50%',
  background: 'var(--clr-crit-bg, #fff5f5)',
  border: '1.5px solid var(--clr-crit-border, #ea4335)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  fontSize: '1.6rem',
  flexShrink: 0,
};

export const ERROR_FALLBACK_TITLE_STYLE: CSSProperties = {
  margin: 0,
  fontSize: '1.1rem',
  fontWeight: 700,
  color: 'var(--clr-text, #1a1c1e)',
};

export const ERROR_FALLBACK_MESSAGE_STYLE: CSSProperties = {
  margin: 0,
  fontSize: '0.88rem',
  color: 'var(--clr-text-muted, #74777f)',
  maxWidth: '280px',
  lineHeight: 1.5,
};

export const ERROR_FALLBACK_DEBUG_STYLE: CSSProperties = {
  margin: 0,
  padding: '8px 12px',
  borderRadius: '8px',
  background: '#f4f4f4',
  fontSize: '0.72rem',
  color: '#555',
  maxWidth: '320px',
  overflowX: 'auto',
  textAlign: 'left',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
};

export const ERROR_FALLBACK_BUTTON_STYLE: CSSProperties = {
  marginTop: '4px',
  padding: '10px 28px',
  borderRadius: '10px',
  border: 'none',
  background: 'var(--clr-crit-border, #ea4335)',
  color: '#fff',
  fontSize: '0.9rem',
  fontWeight: 600,
  cursor: 'pointer',
};

export const SKELETON_CARD_STYLE: CSSProperties = {
  borderColor: 'transparent',
};

export const WORKSHOP_SKELETON_ROW_STYLE: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-end',
  marginTop: '14px',
};

export const WORKSHOP_SKELETON_COLUMN_STYLE: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
  flex: 1,
};

export const UNIT_SKELETON_STATE_STYLE: CSSProperties = {
  marginTop: '8px',
};

export const SKELETON_BLOCK_DEFAULTS = Object.freeze({
  width: '100%',
  height: '16px',
  borderRadius: '6px',
  background: 'linear-gradient(90deg, #eaebec 25%, #f4f5f6 50%, #eaebec 75%)',
  backgroundSize: '200% 100%',
  animation: 'skeleton-shimmer 1.6s ease-in-out infinite',
});

export function getFabButtonStyle(collapsed: boolean, sent: boolean): CSSProperties {
  return {
    position: 'fixed',
    bottom: 'calc(64px + var(--bottom-safe-offset, 0px) + 16px)',
    right: '16px',
    // На узких экранах кнопка должна оставаться читаемой: ширина по контенту,
    // но не больше безопасной области экрана и не уже удобного touch-size.
    width: collapsed ? '52px' : 'max-content',
    maxWidth: collapsed ? '52px' : 'min(210px, calc(100vw - 32px))',
    minWidth: collapsed ? '52px' : '168px',
    height: '52px',
    padding: collapsed ? '0' : '0 18px',
    borderRadius: collapsed ? '50%' : '26px',
    justifyContent: collapsed ? 'center' : 'flex-start',
    background: sent ? UI_PALETTE.success : UI_PALETTE.fabIdle,
    boxShadow: sent
      ? '0 4px 20px rgba(52,168,83,0.4)'
      : collapsed
        ? '0 4px 14px rgba(249,115,22,0.5)'
        : '0 4px 20px rgba(249,115,22,0.4)',
    zIndex: 9,
    display: 'flex',
    alignItems: 'center',
    overflow: 'hidden',
    whiteSpace: 'nowrap',
    border: 'none',
    cursor: 'pointer',
    fontFamily: 'Inter, sans-serif',
    fontSize: '0.85rem',
    fontWeight: 700,
    letterSpacing: '0.01em',
    color: UI_PALETTE.white,
    transition:
      'width 0.3s ease, max-width 0.3s ease, padding 0.3s ease, border-radius 0.3s ease, opacity 0.3s ease, transform 0.3s ease, box-shadow 0.3s ease',
  };
}
