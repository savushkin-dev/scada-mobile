import { API_BASE } from '../config';
import { apiFetchJson } from './client';
import { HttpError } from '../errors/AppError';
import {
  NotificationSettingsSchema,
  UserProfileSchema,
  type NotificationSetting,
  type UserProfile,
} from '../schemas';

export async function fetchUserProfile(signal?: AbortSignal): Promise<UserProfile> {
  const raw = await apiFetchJson(`${API_BASE}/api/v1.0.0/users/me`, { signal });
  return UserProfileSchema.parse(raw);
}

export async function fetchNotificationSettings(
  signal?: AbortSignal
): Promise<NotificationSetting[]> {
  const raw = await apiFetchJson(`${API_BASE}/api/v1.0.0/notifications/settings`, {
    signal,
  });
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
  const resp = await apiFetch(`${API_BASE}/api/v1.0.0/notifications/settings`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });

  if (!resp.ok) throw new HttpError(resp.status);
}
