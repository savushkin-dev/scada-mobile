import { useEffect } from 'react';
import type { AppError } from '../errors/AppError';
import { useAppContext, type HeaderErrorSlot } from '../context/AppContext';

/**
 * Синхронизирует page-scoped ошибку с глобальным header-слотом.
 */
export function useHeaderErrorSlot(
  slot: HeaderErrorSlot,
  error: AppError | null,
  retryAction?: (() => void) | undefined
): void {
  const { setHeaderError, clearHeaderError } = useAppContext();

  useEffect(() => {
    if (error) {
      setHeaderError(slot, error, error.retryable ? retryAction : undefined);
      return () => clearHeaderError(slot);
    }

    clearHeaderError(slot);
    return () => clearHeaderError(slot);
  }, [slot, error, retryAction, setHeaderError, clearHeaderError]);
}
