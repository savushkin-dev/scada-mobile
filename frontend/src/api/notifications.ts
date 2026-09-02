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

export type NotificationHistoryStatus = 'COMPLETED' | 'CANCELLED';

function buildHistoryUrl(
  base: string,
  statuses: NotificationHistoryStatus[],
  page: number,
  size: number
): string {
  const params = new URLSearchParams();
  if (statuses.length > 0) {
    statuses.forEach((status) => params.append('statuses', status));
  }
  params.set('page', String(page));
  params.set('size', String(size));
  return `${base}?${params.toString()}`;
}

/** История отправленных текущим пользователем уведомлений. */
export async function fetchSentHistory(
  statuses: NotificationHistoryStatus[] = ['COMPLETED', 'CANCELLED'],
  page = 0,
  size = 20
): Promise<NotificationWorkflowEntry[]> {
  return WorkflowListSchema.parse(
    await apiFetchJson(
      buildHistoryUrl('/api/v1.0.0/notifications/sent-history', statuses, page, size)
    )
  );
}

/** Завершённые/отменённые уведомления, принятые текущим пользователем. */
export async function fetchExecutorHistory(
  statuses: NotificationHistoryStatus[] = ['COMPLETED', 'CANCELLED'],
  page = 0,
  size = 20
): Promise<NotificationWorkflowEntry[]> {
  return WorkflowListSchema.parse(
    await apiFetchJson(
      buildHistoryUrl('/api/v1.0.0/notifications/executor-history', statuses, page, size)
    )
  );
}
