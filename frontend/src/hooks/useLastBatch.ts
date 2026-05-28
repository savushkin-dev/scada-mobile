import { useCallback, useState } from 'react';
import { API_BASE } from '../config';
import { apiFetch } from '../api/client';

export type LastBatchResult = 'activated' | 'deactivated' | 'already_active' | 'idle';

interface UseLastBatchReturn {
  sending: boolean;
  sent: boolean;
  result: LastBatchResult;
  sendLastBatch: (unitId: string) => Promise<void>;
  reset: () => void;
}

/**
 * Единый источник правды для отправки действия «Последняя партия».
 *
 * POST /api/v1.0.0/line/{unitId}/last-batch
 */
export function useLastBatch(): UseLastBatchReturn {
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [result, setResult] = useState<LastBatchResult>('idle');

  const sendLastBatch = useCallback(
    async (unitId: string) => {
      if (sending) return;
      setSending(true);
      try {
        const resp = await apiFetch(`${API_BASE}/api/v1.0.0/line/${unitId}/last-batch`, {
          method: 'POST',
        });
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const body = (await resp.json()) as { status?: LastBatchResult } | undefined;
        setResult(body?.status ?? 'idle');
      } catch (e) {
        console.warn('[useLastBatch] last-batch fallback:', (e as Error).message);
        setResult('idle');
      }
      setSent(true);
      setSending(false);
    },
    [sending]
  );

  const reset = useCallback(() => {
    setSent(false);
    setResult('idle');
  }, []);

  return { sending, sent, result, sendLastBatch, reset };
}
