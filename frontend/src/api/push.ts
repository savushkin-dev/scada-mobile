import { API_BASE } from '../config';
import { HttpError } from '../errors/AppError';

export interface PushPublicKeyResponse {
  enabled: boolean;
  publicKey: string | null;
}

export interface PushSubscriptionRegistration {
  installationId: string;
  platform: 'android' | 'desktop' | 'unknown';
  appChannel: 'PWA' | 'TWA';
  preferredWorkshopId: string | null;
  preferredUnitId: string | null;
  subscription: {
    endpoint: string;
    keys: {
      p256dh: string;
      auth: string;
    };
  };
}

export async function fetchPushPublicKey(signal?: AbortSignal): Promise<PushPublicKeyResponse> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/push/public-key`, {
    method: 'GET',
    signal,
    cache: 'no-store',
  });

  if (!resp.ok) throw new HttpError(resp.status);

  const raw = (await resp.json()) as Partial<PushPublicKeyResponse>;
  return {
    enabled: raw.enabled === true,
    publicKey: typeof raw.publicKey === 'string' ? raw.publicKey : null,
  };
}

export async function registerPushSubscription(
  payload: PushSubscriptionRegistration,
  signal?: AbortSignal
): Promise<void> {
  const resp = await fetch(`${API_BASE}/api/v1.0.0/push/subscriptions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
    signal,
  });

  if (!resp.ok) throw new HttpError(resp.status);
}

export async function deactivatePushSubscription(
  installationId: string,
  signal?: AbortSignal
): Promise<void> {
  const url = new URL(`${API_BASE}/api/v1.0.0/push/subscriptions`);
  url.searchParams.set('installationId', installationId);

  const resp = await fetch(url.toString(), {
    method: 'DELETE',
    signal,
  });

  if (resp.status === 404) return;
  if (!resp.ok) throw new HttpError(resp.status);
}
