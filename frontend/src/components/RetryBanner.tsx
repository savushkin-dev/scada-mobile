/**
 * RetryBanner — показывается только когда все попытки загрузки исчерпаны.
 * Возобновляет загрузку вручную.
 *
 * Ретраи прозрачны для пользователя: во время повторных попыток
 * отображается обычное состояние загрузки (скелетон/индикатор).
 */

import type { AppError } from '../errors/AppError';
import {
  FLEX_GROW_STYLE,
  RETRY_BANNER_STYLE,
  RETRY_BUTTON_STYLE,
  RETRY_ICON_STYLE,
  UI_COPY,
} from '../config';

interface RetryBannerProps {
  /** Не null — все попытки исчерпаны, показываем баннер. */
  error: AppError | null;
  onRetry: () => void;
}

export function RetryBanner({ error, onRetry }: RetryBannerProps) {
  if (!error) return null;

  return (
    <div role="alert" aria-live="assertive" style={RETRY_BANNER_STYLE}>
      <span style={FLEX_GROW_STYLE}>
        <span style={RETRY_ICON_STYLE}>{UI_COPY.retryIcon}</span>
        {/* Сообщение уже человекочитаемое — сформировано classifyError. */}
        {error.message}
      </span>
      {/* Кнопка повтора показывается только если ошибка повторяема: 404 — не показываем. */}
      {error.retryable && (
        <button onClick={onRetry} style={RETRY_BUTTON_STYLE}>
          {UI_COPY.retryAction}
        </button>
      )}
    </div>
  );
}
