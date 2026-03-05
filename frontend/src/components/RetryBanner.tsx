/**
 * RetryBanner — показывается только когда все попытки загрузки исчерпаны.
 * Возобновляет загрузку вручную.
 *
 * Ретраи прозрачны для пользователя: во время повторных попыток
 * отображается обычное состояние загрузки (скелетон/индикатор).
 */

import type { AppError } from '../errors/AppError';

interface RetryBannerProps {
  /** Не null — все попытки исчерпаны, показываем баннер. */
  error: AppError | null;
  onRetry: () => void;
}

export function RetryBanner({ error, onRetry }: RetryBannerProps) {
  if (!error) return null;

  return (
    <div
      role="alert"
      aria-live="assertive"
      style={{
        margin: '0 16px 12px',
        borderRadius: '12px',
        padding: '12px 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '12px',
        fontSize: '0.82rem',
        lineHeight: '1.4',
        background: '#FFF0F0',
        border: '1px solid #FFCDD2',
        color: '#B71C1C',
        flexShrink: 0,
      }}
    >
      <span style={{ flex: 1 }}>
        <span style={{ marginRight: '6px' }}>⚠</span>
        {/* Сообщение уже человекочитаемое — сформировано classifyError. */}
        {error.message}
      </span>
      {/* Кнопка повтора показывается только если ошибка повторяема: 404 — не показываем. */}
      {error.retryable && (
        <button
          onClick={onRetry}
          style={{
            flexShrink: 0,
            background: 'none',
            border: '1.5px solid currentColor',
            borderRadius: '8px',
            padding: '4px 12px',
            cursor: 'pointer',
            fontSize: '0.8rem',
            fontWeight: 600,
            color: 'inherit',
            lineHeight: '1.4',
          }}
        >
          Повторить
        </button>
      )}
    </div>
  );
}
