import type { NotificationWsMessage } from '../types';

type NotificationSwMessage =
  | { type: 'NOTIFICATION_EVENT'; payload: NotificationWsMessage }
  | { type: 'NOTIFICATION_SNAPSHOT'; payload: NotificationWsMessage[] };

function canUseNotifications(): boolean {
  return 'Notification' in window && 'serviceWorker' in navigator;
}

async function ensureNotificationPermission(): Promise<boolean> {
  if (!canUseNotifications()) return false;
  if (Notification.permission === 'granted') return true;
  if (Notification.permission === 'denied') return false;

  const result = await Notification.requestPermission();
  return result === 'granted';
}

async function postToServiceWorker(message: NotificationSwMessage): Promise<void> {
  if (!canUseNotifications()) return;
  const allowed = await ensureNotificationPermission();
  if (!allowed) return;

  const registration = await navigator.serviceWorker.ready;
  const worker = registration.active ?? registration.waiting ?? registration.installing;
  if (!worker) return;

  worker.postMessage(message);
}

export async function syncNotificationSnapshot(
  notifications: NotificationWsMessage[]
): Promise<void> {
  await postToServiceWorker({ type: 'NOTIFICATION_SNAPSHOT', payload: notifications });
}

export async function pushNotificationEvent(message: NotificationWsMessage): Promise<void> {
  await postToServiceWorker({ type: 'NOTIFICATION_EVENT', payload: message });
}
