import { API_BASE, HTTP_REQUEST } from '../config';
import { getAuthUserId } from '../auth/session';
import { HttpError } from '../errors/AppError';
import {
  NotificationSettingsSchema,
  UserProfileSchema,
  type NotificationSetting,
  type UserProfile,
} from '../schemas';

export async function fetchUserProfile(signal?: AbortSignal): Promise<UserProfile> {
  const headers: Record<string, string> = {};
  const userId = getAuthUserId();
  if (userId) headers['X-User-Id'] = userId;

  const resp = await fetch(`${API_BASE}/api/v1.0.0/users/me`, { signal, headers });

  if (!resp.ok) throw new HttpError(resp.status);

  const raw = await resp.json();
  return UserProfileSchema.parse(raw);
}

export async function fetchNotificationSettings(
  signal?: AbortSignal
): Promise<NotificationSetting[]> {
  const headers: Record<string, string> = {};
  const userId = getAuthUserId();
  if (userId) headers['X-User-Id'] = userId;

  const resp = await fetch(`${API_BASE}/api/v1.0.0/notifications/settings`, {
    signal,
    headers,
  });

  if (!resp.ok) throw new HttpError(resp.status);

  const raw = await resp.json();
  return NotificationSettingsSchema.parse(raw);
}

export interface UpdateNotificationSettingPayload {
  unitId: string;
  techEnabled: boolean;
  masterEnabled: boolean;
}

export async function updateNotificationSetting(
  payload: UpdateNotificationSettingPayload
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': HTTP_REQUEST.jsonContentType,
  };
  const userId = getAuthUserId();
  if (userId) headers['X-User-Id'] = userId;

  const resp = await fetch(`${API_BASE}/api/v1.0.0/notifications/settings`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(payload),
  });

  if (!resp.ok) throw new HttpError(resp.status);
}
