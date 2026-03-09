/**
 * ErrorBoundary — последний рубеж перехвата ошибок рендера.
 *
 * ─ Аналог Spring BasicErrorController / Filter-уровня обработки ─────────────
 * В Spring `BasicErrorController` или `HandlerExceptionResolver` перехватывает
 * исключения, которые не были обработаны на уровне конкретного контроллера,
 * и возвращает стандартный error-ответ.
 *
 * Здесь React ErrorBoundary выполняет ту же роль: если ошибка вырвалась
 * за пределы компонентного дерева (render/lifecycle crash), она попадает
 * сюда, а не роняет всё приложение с пустым экраном.
 *
 * Особенности:
 * - ErrorBoundary обязан быть class-компонентом (ограничение React).
 * - Все пойманные ошибки проходят через classifyError — единственный
 *   классификатор — и отображаются с человекочитаемым сообщением.
 * - Кнопка «Перезагрузить» полностью сбрасывает состояние границы
 *   и перемонтирует дочернее дерево.
 * - componentDidCatch логирует полный stack trace в консоль —
 *   для отладки в dev и потенциального подключения Sentry / аналога.
 */

import { Component, type ErrorInfo, type ReactNode } from 'react';
import {
  ERROR_FALLBACK_BUTTON_STYLE,
  ERROR_FALLBACK_CONTAINER_STYLE,
  ERROR_FALLBACK_DEBUG_STYLE,
  ERROR_FALLBACK_ICON_STYLE,
  ERROR_FALLBACK_MESSAGE_STYLE,
  ERROR_FALLBACK_TITLE_STYLE,
  UI_COPY,
} from '../config';
import { classifyError } from './classifyError';
import type { AppError } from './AppError';

// ── Props / State ─────────────────────────────────────────────────────

interface ErrorBoundaryProps {
  children: ReactNode;
  /**
   * Опциональный кастомный fallback вместо встроенного экрана ошибки.
   * Принимает типизированную ошибку и callback для сброса состояния.
   */
  fallback?: (error: AppError, reset: () => void) => ReactNode;
}

interface ErrorBoundaryState {
  appError: AppError | null;
}

// ── Компонент ─────────────────────────────────────────────────────────

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { appError: null };
    this.reset = this.reset.bind(this);
  }

  // getDerivedStateFromError — синхронный: устанавливает состояние ошибки.
  // Запускается во время рендера (фаза render), поэтому не должен иметь
  // побочных эффектов. Классификация ошибки происходит здесь.
  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    return { appError: classifyError(error, 'ui/render') };
  }

  // componentDidCatch — асинхронный: для логирования и внешних репортеров.
  // Получает полный errorInfo со stack trace дерева компонентов.
  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[ErrorBoundary] Неперехваченная ошибка рендера:', error);
    console.error('[ErrorBoundary] Дерево компонентов:', info.componentStack);
    // TODO: здесь можно добавить интеграцию с Sentry / аналогом:
    // Sentry.captureException(error, { extra: { componentStack: info.componentStack } });
  }

  reset(): void {
    this.setState({ appError: null });
  }

  render(): ReactNode {
    const { appError } = this.state;
    const { children, fallback } = this.props;

    if (!appError) return children;

    // Кастомный fallback, если передан.
    if (fallback) return fallback(appError, this.reset);

    // Встроенный fallback — минималистичный экран ошибки.
    return <ErrorFallback error={appError} onReset={this.reset} />;
  }
}

// ── Встроенный fallback-экран ─────────────────────────────────────────
// ErrorFallback — приватный хелпер рендера, не экспортируется.
// Живёт в одном файле с ErrorBoundary (class-компонент + render helper —
// типовой паттерн), поэтому fast-refresh lint-правило отключено локально.

interface ErrorFallbackProps {
  error: AppError;
  onReset: () => void;
}

// eslint-disable-next-line react-refresh/only-export-components
function ErrorFallback({ error, onReset }: ErrorFallbackProps) {
  return (
    <div role="alert" style={ERROR_FALLBACK_CONTAINER_STYLE}>
      {/* Иконка */}
      <div style={ERROR_FALLBACK_ICON_STYLE}>{UI_COPY.retryIcon}</div>

      {/* Заголовок */}
      <h2 style={ERROR_FALLBACK_TITLE_STYLE}>{UI_COPY.errorFallbackTitle}</h2>

      {/* Сообщение из classifyError — всегда на русском */}
      <p style={ERROR_FALLBACK_MESSAGE_STYLE}>{error.message}</p>

      {/* Техническое сообщение — только в dev */}
      {import.meta.env.DEV && <pre style={ERROR_FALLBACK_DEBUG_STYLE}>{error.raw}</pre>}

      {/* Кнопка перезагрузки */}
      <button onClick={onReset} style={ERROR_FALLBACK_BUTTON_STYLE}>
        {UI_COPY.errorFallbackReload}
      </button>
    </div>
  );
}
