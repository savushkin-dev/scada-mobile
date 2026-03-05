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
    <div
      role="alert"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100dvh',
        padding: '24px',
        background: 'var(--clr-bg-app, #f8f9fa)',
        textAlign: 'center',
        gap: '16px',
      }}
    >
      {/* Иконка */}
      <div
        style={{
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
        }}
      >
        ⚠
      </div>

      {/* Заголовок */}
      <h2
        style={{
          margin: 0,
          fontSize: '1.1rem',
          fontWeight: 700,
          color: 'var(--clr-text, #1a1c1e)',
        }}
      >
        Что-то пошло не так
      </h2>

      {/* Сообщение из classifyError — всегда на русском */}
      <p
        style={{
          margin: 0,
          fontSize: '0.88rem',
          color: 'var(--clr-text-muted, #74777f)',
          maxWidth: '280px',
          lineHeight: 1.5,
        }}
      >
        {error.message}
      </p>

      {/* Техническое сообщение — только в dev */}
      {import.meta.env.DEV && (
        <pre
          style={{
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
          }}
        >
          {error.raw}
        </pre>
      )}

      {/* Кнопка перезагрузки */}
      <button
        onClick={onReset}
        style={{
          marginTop: '4px',
          padding: '10px 28px',
          borderRadius: '10px',
          border: 'none',
          background: 'var(--clr-crit-border, #ea4335)',
          color: '#fff',
          fontSize: '0.9rem',
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        Перезагрузить
      </button>
    </div>
  );
}
