import { z } from 'zod';
import { apiFetchJson } from './client';

export type NotificationAction = 'accept' | 'complete' | 'cancel';

export async function updateNotification(notificationId: number, action: NotificationAction) {
  return apiFetchJson(`/api/v1.0.0/notifications/${notificationId}/${action}`, {
    method: 'POST',
  });
}

/**
 * Запись workflow-уведомления из REST (history / my-tasks / incoming).
 * Соответствует NotificationWorkflowResponseDTO backend'а.
 */
export const NotificationWorkflowEntrySchema = z.object({
  notificationId: z.number(),
  unitId: z.string(),
  unitName: z.string().nullable(),
  creatorId: z.string(),
  creatorName: z.string().nullable(),
  status: z.enum(['PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']),
  activatedAt: z.string(),
  acceptedBy: z.string().nullable(),
  acceptedByName: z.string().nullable(),
  acceptedAt: z.string().nullable(),
  completedAt: z.string().nullable(),
  cancelledAt: z.string().nullable(),
  version: z.number(),
});

export type NotificationWorkflowEntry = z.infer<typeof NotificationWorkflowEntrySchema>;

const WorkflowListSchema = z.array(NotificationWorkflowEntrySchema);

/** История отправленных текущим пользователем уведомлений (все статусы, новые сверху). */
export async function fetchSentHistory(): Promise<NotificationWorkflowEntry[]> {
  return WorkflowListSchema.parse(await apiFetchJson('/api/v1.0.0/notifications/sent-history'));
}

/** Уведомления, принятые текущим пользователем (в работе и завершённые). */
export async function fetchExecutorHistory(): Promise<NotificationWorkflowEntry[]> {
  return WorkflowListSchema.parse(await apiFetchJson('/api/v1.0.0/notifications/executor-history'));
}
