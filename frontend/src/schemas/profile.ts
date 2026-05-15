/**
 * Zod-схемы для профиля пользователя и настроек уведомлений.
 *
 * Эти схемы — единственный источник правды для типов,
 * используемых на странице профиля и в overlay-настройках.
 */
import { z } from 'zod';

export const AssignedUnitSchema = z.object({
  unitId: z.string(),
  unitName: z.string(),
  printsrvInstanceId: z.string().nullable().optional(),
});

export const UserProfileSchema = z.object({
  fullName: z.string(),
  role: z.string(),
  workerCode: z.string(),
  assignedUnits: z.array(AssignedUnitSchema),
});

export const NotificationSettingSchema = z.object({
  unitId: z.string(),
  unitName: z.string(),
  techEnabled: z.boolean(),
  masterEnabled: z.boolean(),
});

export const NotificationSettingsSchema = z.array(NotificationSettingSchema);

export type UserProfile = z.infer<typeof UserProfileSchema>;
export type NotificationSetting = z.infer<typeof NotificationSettingSchema>;
