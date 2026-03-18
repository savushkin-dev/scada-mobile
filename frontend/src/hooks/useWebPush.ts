import { useEffect, useRef } from 'react';
import { syncWebPushSubscription } from '../lib/webPush';

interface ServiceWorkerMessage {
  type?: string;
}

export function useWebPush(): void {
  const isSyncRunningRef = useRef(false);

  useEffect(() => {
    if (!('serviceWorker' in navigator)) {
      return;
    }

    const runSync = async () => {
      if (isSyncRunningRef.current) {
        return;
      }

      isSyncRunningRef.current = true;
      try {
        await syncWebPushSubscription();
      } catch (error) {
        console.error('[push] Subscription sync failed:', error);
      } finally {
        isSyncRunningRef.current = false;
      }
    };

    const onServiceWorkerMessage = (event: MessageEvent<ServiceWorkerMessage>) => {
      if (event.data?.type === 'PUSH_SUBSCRIPTION_CHANGE') {
        void runSync();
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void runSync();
      }
    };

    navigator.serviceWorker.addEventListener('message', onServiceWorkerMessage);
    document.addEventListener('visibilitychange', onVisibilityChange);

    void runSync();

    return () => {
      navigator.serviceWorker.removeEventListener('message', onServiceWorkerMessage);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, []);
}
