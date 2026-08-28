import { apiFetchJson } from './client';

export type NotificationAction = 'accept' | 'complete' | 'cancel';

export async function updateNotification(notificationId: number, action: NotificationAction) {
  return apiFetchJson(`/api/v1.0.0/notifications/${notificationId}/${action}`, {
    method: 'POST',
  });
}
