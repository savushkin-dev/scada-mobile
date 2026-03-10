import { createContext, useContext } from 'react';
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
  lineData: LineStatusPayload | null;
  devicesData: DevicesStatusPayload | null;
  devicesTopology: DevicesTopology | null;
  devicesLoading: boolean;
  queueData: QueuePayload | null;
  errorsData: ErrorsPayload | null;
}

const DetailsContext = createContext<DetailsContextValue | null>(null);

export const DetailsProvider = DetailsContext.Provider;

// eslint-disable-next-line react-refresh/only-export-components
export function useDetailsContext(): DetailsContextValue {
  const ctx = useContext(DetailsContext);
  if (!ctx) throw new Error('useDetailsContext must be used within DetailsProvider');
  return ctx;
}
