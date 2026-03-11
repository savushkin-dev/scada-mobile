import type { ReactNode } from 'react';
import { getErrorBodyMessage } from '../errors/AppError';
import type { AppError } from '../errors/AppError';

interface TabContentStateProps {
  /**
   * Показывает skeleton вместо контента.
   * Приоритет выше error: пока isLoading, skeleton всегда отображается.
   */
  isLoading: boolean;
  /**
   * Ошибка для отображения вместо контента.
   * null = ошибки нет, рендерить children.
   */
  error: AppError | null;
  /** Placeholder, отображаемый пока isLoading === true. */
  skeleton: ReactNode;
  /** Активный контент вкладки / секции. */
  children: ReactNode;
}

/**
 * Единый источник правды для паттерна «loading → error → data» во вкладках.
 *
 * Логика:
 *  1. isLoading → skeleton
 *  2. error     → краткое пользовательское сообщение (getErrorBodyMessage)
 *  3. иначе     → children
 *
 * Для добавления новой вкладки или секции: передать isLoading, error,
 * skeleton-плейсхолдер и контент как children.
 * Детальный текст ошибки — в AppError.raw, не показывается пользователю.
 */
export function TabContentState({ isLoading, error, skeleton, children }: TabContentStateProps) {
  if (isLoading) return <>{skeleton}</>;

  if (error) {
    return (
      <p className="text-center text-[#74777F] py-10 text-[0.88rem]">
        {getErrorBodyMessage(error)}
      </p>
    );
  }

  return <>{children}</>;
}
