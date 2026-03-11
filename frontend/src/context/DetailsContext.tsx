import { createContext, useContext } from 'react';
import type { AppError } from '../errors/AppError';
import type { SignalState } from './AppContext';
import type {
  DevicesStatusPayload,
  DevicesTopology,
  ErrorsPayload,
  LineStatusPayload,
  QueuePayload,
} from '../types';

/**
 * Данные, предоставляемые {@link DetailsLayout} вложенным табам через контекст.
 *
 * Вместо пробрасывания пропсов в каждый таб-компонент,
 * все WS- и REST-данные доступны через единый `useDetailsContext()`.
 */
export interface DetailsContextValue {
  // ── WS-данные аппарата ────────────────────────────────────────────
  lineData: LineStatusPayload | null;
  devicesData: DevicesStatusPayload | null;
  queueData: QueuePayload | null;
  errorsData: ErrorsPayload | null;
  // ── REST-данные (topology устройств) ──────────────────────────────
  devicesTopology: DevicesTopology | null;
  devicesLoading: boolean;
  topologyError: AppError | null;
  // ── Сигнал WS-соединения с аппаратом ──────────────────────────────
  /**
   * Состояние WS-канала `/ws/unit/{unitId}`.
   * Используется вкладками для отображения skeleton / ошибки:
   *  - idle / reconnecting + data===null → skeleton
   *  - error + data===null              → сообщение об ошибке
   *  - connected / есть кешированные данные → контент
   */
  unitSignal: SignalState;
  /** Последняя ошибка WS-канала аппарата (null = нет ошибки). */
  unitError: AppError | null;
  /**
   * Агрегированная ошибка страницы: первый непустой слот из unit → topology.
   *
   * Вычисляется через {@link usePageError} в DetailsLayout.
   * Вкладки проверяют именно это поле — не timing-зависимые состояния
   * отдельных каналов — чтобы синхронно переходить из skeleton в ошибку
   * как только ЛЮБОЙ источник данных страницы сообщил об ошибке.
   */
  pageError: AppError | null;
}

const DetailsContext = createContext<DetailsContextValue | null>(null);

export const DetailsProvider = DetailsContext.Provider;

// eslint-disable-next-line react-refresh/only-export-components
export function useDetailsContext(): DetailsContextValue {
  const ctx = useContext(DetailsContext);
  if (!ctx) throw new Error('useDetailsContext must be used within DetailsProvider');
  return ctx;
}
