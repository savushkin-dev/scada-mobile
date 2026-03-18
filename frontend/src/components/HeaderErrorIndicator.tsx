import { UI_COPY } from '../config';
import { useAppContext } from '../context/AppContext';

/**
 * Компактный индикатор ошибки в шапке.
 *
 * Показывает только факт ошибки и не дублирует детали.
 * Детальная расшифровка ошибки отображается на уровне страницы
 * через getErrorBodyMessage(...) в page-компонентах.
 */

export function HeaderErrorIndicator() {
  const { headerError } = useAppContext();

  if (!headerError) return null;

  return (
    <div className="ml-auto flex max-w-[48vw] items-center justify-end sm:max-w-[20rem]">
      <div
        role="status"
        aria-live="polite"
        className="flex items-center gap-2 rounded-full border border-[#d9b4b8] bg-white/80 px-3 py-2 text-xs font-semibold text-[#9f3138] shadow-sm backdrop-blur-sm"
      >
        <span className="text-sm leading-none" aria-hidden="true">
          {UI_COPY.retryIcon}
        </span>
        <span className="max-w-[24vw] truncate sm:max-w-[12rem]">{UI_COPY.headerErrorLabel}</span>
      </div>
    </div>
  );
}
