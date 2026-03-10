import { BACK_BUTTON_STYLE, UI_COPY } from '../config';
import { HeaderErrorIndicator } from './HeaderErrorIndicator';

/**
 * Единственная шапка приложения.
 *
 * Рендерится один раз в {@link RootLayout}, содержимое управляется
 * из страниц через хук `usePageHeader()`.
 *
 * Визуально шапка всегда закреплена в верхней части экрана (flex-shrink: 0)
 * и не прокручивается вместе с контентом.
 */

interface PageHeaderProps {
  /** Основной заголовок страницы */
  title: string;
  /** Маленький надзаголовок над title (необязателен) */
  subtitle?: string;
  /** Компактный режим для вложенных/детальных экранов. */
  variant?: 'default' | 'compact';
  /**
   * Если передан — рендерится кнопка «←» и вызывается при клике.
   * Отсутствие пропа означает корневую страницу без кнопки назад.
   */
  onBack?: () => void;
}

export function PageHeader({ title, subtitle, variant = 'default', onBack }: PageHeaderProps) {
  const isCompact = variant === 'compact';
  const headerClassName = [
    'z-10 backdrop-blur-md bg-[#f8f9fa]/30 border-b border-white/15 flex items-center gap-3 flex-shrink-0',
    isCompact ? 'px-5 py-4 sm:px-6 lg:px-8' : 'px-6 pt-5 pb-4 sm:px-8 lg:px-10',
  ].join(' ');

  const titleClassName = isCompact
    ? 'text-base font-bold text-[#1A1C1E] leading-tight truncate'
    : onBack
      ? 'text-xl font-bold text-[#1A1C1E] leading-tight'
      : 'text-2xl font-bold text-[#1A1C1E]';

  return (
    <header className={headerClassName}>
      <div className="flex min-w-0 flex-1 items-center gap-3 overflow-hidden">
        {onBack && (
          <button
            onClick={onBack}
            style={BACK_BUTTON_STYLE}
            aria-label={UI_COPY.backButtonAriaLabel}
          >
            {UI_COPY.backIcon}
          </button>
        )}
        <div className="min-w-0 overflow-hidden">
          {subtitle && (
            <p
              className={
                isCompact
                  ? 'text-[10px] font-bold tracking-[0.08em] text-[#74777F] uppercase mb-0.5'
                  : 'text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-1'
              }
            >
              {subtitle}
            </p>
          )}
          <h1 className={titleClassName}>{title}</h1>
        </div>
      </div>
      <HeaderErrorIndicator />
    </header>
  );
}
