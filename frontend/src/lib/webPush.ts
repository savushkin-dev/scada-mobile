import {
  deactivatePushSubscription,
  fetchPushPublicKey,
  registerPushSubscription,
  type PushSubscriptionRegistration,
} from '../api/push';

const INSTALLATION_ID_KEY = 'scada.push.installationId';
const LAST_PERMISSION_PROMPT_AT_KEY = 'scada.push.lastPromptAt';
const PERMISSION_PROMPT_COOLDOWN_MS = 24 * 60 * 60 * 1000;

function getOrCreateInstallationId(): string {
  const existing = localStorage.getItem(INSTALLATION_ID_KEY);
  if (existing && existing.trim().length > 0) {
    return existing;
  }

  const id =
    typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `scada-${Date.now()}-${Math.random().toString(16).slice(2)}`;

  localStorage.setItem(INSTALLATION_ID_KEY, id);
  return id;
}

function isStandaloneDisplayMode(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    // iOS Safari fallback.
    (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

function detectAppChannel(): 'PWA' | 'TWA' {
  if (document.referrer.startsWith('android-app://')) {
    return 'TWA';
  }
  return 'PWA';
}

function shouldPromptForPermission(appChannel: 'PWA' | 'TWA'): boolean {
  if (appChannel === 'TWA') {
    return true;
  }
  return isStandaloneDisplayMode();
}

function canPromptNow(): boolean {
  const raw = localStorage.getItem(LAST_PERMISSION_PROMPT_AT_KEY);
  const lastPromptAt = raw ? Number(raw) : 0;
  if (!Number.isFinite(lastPromptAt) || lastPromptAt <= 0) {
    return true;
  }
  return Date.now() - lastPromptAt >= PERMISSION_PROMPT_COOLDOWN_MS;
}

function markPromptAttempt(): void {
  localStorage.setItem(LAST_PERMISSION_PROMPT_AT_KEY, String(Date.now()));
}

function getPlatform(): 'android' | 'desktop' | 'unknown' {
  const ua = navigator.userAgent.toLowerCase();
  if (ua.includes('android')) return 'android';
  if (ua.includes('windows') || ua.includes('mac') || ua.includes('linux')) return 'desktop';
  return 'unknown';
}

function urlBase64ToArrayBuffer(base64String: string): ArrayBuffer {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i += 1) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray.buffer as ArrayBuffer;
}

function arrayBufferToBase64Url(input: ArrayBuffer | null): string {
  if (!input) return '';

  const bytes = new Uint8Array(input);
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }

  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function toRegistrationPayload(
  installationId: string,
  appChannel: 'PWA' | 'TWA',
  subscription: PushSubscription
): PushSubscriptionRegistration {
  return {
    installationId,
    platform: getPlatform(),
    appChannel,
    preferredWorkshopId: null,
    preferredUnitId: null,
    subscription: {
      endpoint: subscription.endpoint,
      keys: {
        p256dh: arrayBufferToBase64Url(subscription.getKey('p256dh')),
        auth: arrayBufferToBase64Url(subscription.getKey('auth')),
      },
    },
  };
}

export async function syncWebPushSubscription(): Promise<void> {
  if (!window.isSecureContext) return;
  if (
    !('Notification' in window) ||
    !('serviceWorker' in navigator) ||
    !('PushManager' in window)
  ) {
    return;
  }

  const appChannel = detectAppChannel();
  const installationId = getOrCreateInstallationId();
  const registration = await navigator.serviceWorker.ready;
  const existingSubscription = await registration.pushManager.getSubscription();

  if (Notification.permission === 'denied') {
    if (existingSubscription) {
      await existingSubscription.unsubscribe();
    }
    await deactivatePushSubscription(installationId);
    return;
  }

  const pushConfig = await fetchPushPublicKey();
  if (!pushConfig.enabled || !pushConfig.publicKey) {
    return;
  }

  let permission: NotificationPermission = Notification.permission;
  if (permission === 'default' && shouldPromptForPermission(appChannel) && canPromptNow()) {
    markPromptAttempt();
    permission = await Notification.requestPermission();
  }

  if (permission !== 'granted') {
    return;
  }

  const subscription =
    existingSubscription ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToArrayBuffer(pushConfig.publicKey),
    }));

  const payload = toRegistrationPayload(installationId, appChannel, subscription);
  await registerPushSubscription(payload);
}
