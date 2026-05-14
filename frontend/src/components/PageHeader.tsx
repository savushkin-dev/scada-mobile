import { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BACK_BUTTON_STYLE, UI_COPY } from '../config';
import { useAuth } from '../context/AuthContext';
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

export function PageHeader({ title, subtitle, onBack }: PageHeaderProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  const headerClassName =
    'z-10 h-[88px] backdrop-blur-md bg-[#f8f9fa]/30 border-b border-white/15 flex items-center gap-3 flex-shrink-0 px-6 py-4 sm:px-8 lg:px-10';

  const titleClassName = 'text-xl font-bold text-[#1A1C1E] leading-tight truncate';

  const isProfileRoute = location.pathname.startsWith('/profile');

  const handleProfileClick = useCallback(() => {
    if (isProfileRoute) return;
    const target = isAuthenticated ? '/profile' : '/login';
    navigate(target, { state: { from: location } });
  }, [navigate, location, isAuthenticated, isProfileRoute]);

  return (
    <header className={headerClassName}>
      <div className="flex min-w-0 flex-1 items-center gap-3 overflow-hidden">
        {onBack ? (
          <button
            onClick={onBack}
            style={BACK_BUTTON_STYLE}
            aria-label={UI_COPY.backButtonAriaLabel}
          >
            {UI_COPY.backIcon}
          </button>
        ) : (
          <div aria-hidden="true" className="h-10 w-10 flex-shrink-0" />
        )}
        <div className="min-w-0 overflow-hidden flex flex-col justify-center min-h-[40px]">
          {subtitle ? (
            <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-1">
              {subtitle}
            </p>
          ) : null}
          <h1 className={titleClassName}>{title}</h1>
        </div>
      </div>
      <div className="ml-auto flex items-center gap-3">
        <HeaderErrorIndicator />
        <button
          type="button"
          onClick={handleProfileClick}
          aria-label={UI_COPY.profileButtonAriaLabel}
          aria-pressed={isProfileRoute}
          className={
            'flex h-10 w-10 items-center justify-center rounded-full backdrop-blur-sm transition-all duration-200 ease-in-out active:scale-[0.98] ' +
            (isProfileRoute
              ? 'border border-[#2b2f36] bg-[#2b2f36] text-white shadow-[0_0_10px_rgba(17,24,39,0.18)]'
              : 'border-0 bg-transparent text-[#1A1C1E] shadow-none')
          }
        >
          <img
            src="/assets/user-tie.svg"
            alt=""
            aria-hidden="true"
            className={
              'h-5 w-5 transition-all duration-200 ease-in-out ' +
              (isProfileRoute ? 'invert' : 'invert-0')
            }
          />
        </button>
      </div>
    </header>
  );
}
