import { BACK_BUTTON_STYLE, UI_COPY } from '../config';

/**
 * Универсальная закреплённая шапка страницы.
 *
 * Используется на DashboardPage (главная, без кнопки назад)
 * и WorkshopPage (вложенная, с кнопкой назад).
 *
 * Позиционирование: position: sticky; top: 0 — шапка фиксируется
 * внутри ближайшего прокручиваемого предка (overflow-y: auto).
 */

interface PageHeaderProps {
  /** Основной заголовок страницы */
  title: string;
  /** Маленький надзаголовок над title (необязателен) */
  subtitle?: string;
  /**
   * Если передан — рендерится кнопка «←» и вызывается при клике.
   * Отсутствие пропа означает корневую страницу без кнопки назад.
   */
  onBack?: () => void;
}

export function PageHeader({ title, subtitle, onBack }: PageHeaderProps) {
  return (
    <header className="sticky top-0 z-10 backdrop-blur-md bg-[#f8f9fa]/30 border-b border-white/15 flex items-center gap-3 px-6 pt-5 pb-4 flex-shrink-0 sm:px-8 lg:px-10">
      {onBack && (
        <button onClick={onBack} style={BACK_BUTTON_STYLE} aria-label={UI_COPY.backButtonAriaLabel}>
          {UI_COPY.backIcon}
        </button>
      )}
      <div>
        {subtitle && (
          <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-1">
            {subtitle}
          </p>
        )}
        <h1
          className={
            onBack
              ? 'text-xl font-bold text-[#1A1C1E] leading-tight'
              : 'text-2xl font-bold text-[#1A1C1E]'
          }
        >
          {title}
        </h1>
      </div>
    </header>
  );
}
