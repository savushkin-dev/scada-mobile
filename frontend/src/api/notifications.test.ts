import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchExecutorHistory,
  fetchSentHistory,
  updateNotification,
  NotificationWorkflowEntrySchema,
} from './notifications';

const { apiFetchJsonMock } = vi.hoisted(() => ({
  apiFetchJsonMock: vi.fn(),
}));

vi.mock('./client', () => ({
  apiFetch: vi.fn(),
  apiFetchJson: apiFetchJsonMock,
  getServerErrorMessage: vi.fn(),
}));

const entry = {
  notificationId: 5,
  unitId: 'Printer11',
  unitName: 'Принтер 11',
  creatorId: 'user-1',
  creatorName: 'Иван',
  status: 'COMPLETED',
  activatedAt: '2026-09-25T10:00:00',
  acceptedBy: 'user-2',
  acceptedByName: 'Пётр',
  acceptedAt: '2026-09-25T10:05:00',
  completedAt: '2026-09-25T10:10:00',
  cancelledAt: null,
  version: 3,
  curItem: '1605 | 328 | 25.09.2026',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('updateNotification', () => {
  it('POST /notifications/{id}/{action} — URL и method', async () => {
    apiFetchJsonMock.mockResolvedValue({ ok: true });
    await updateNotification(7, 'accept');
    const [url, init] = apiFetchJsonMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1.0.0/notifications/7/accept');
    expect(init.method).toBe('POST');
  });

  it('действия complete/cancel подставляются в URL', async () => {
    apiFetchJsonMock.mockResolvedValue({});
    await updateNotification(1, 'complete');
    await updateNotification(2, 'cancel');
    expect(apiFetchJsonMock.mock.calls[0][0]).toBe('/api/v1.0.0/notifications/1/complete');
    expect(apiFetchJsonMock.mock.calls[1][0]).toBe('/api/v1.0.0/notifications/2/cancel');
  });
});

describe('NotificationWorkflowEntrySchema', () => {
  it('валидная запись парсится', () => {
    expect(NotificationWorkflowEntrySchema.parse(entry).notificationId).toBe(5);
  });

  it('curItem опционален', () => {
    const { curItem: _omit, ...without } = entry;
    expect(NotificationWorkflowEntrySchema.parse(without).curItem).toBeUndefined();
  });
});

describe('fetchSentHistory', () => {
  it('URL с дефолтными statuses/page/size, возвращает распарсенный список', async () => {
    apiFetchJsonMock.mockResolvedValue([entry]);
    const result = await fetchSentHistory();
    const [url] = apiFetchJsonMock.mock.calls[0] as [string];
    expect(url).toBe(
      '/api/v1.0.0/notifications/sent-history?statuses=COMPLETED&statuses=CANCELLED&page=0&size=20'
    );
    expect(result).toHaveLength(1);
    expect(result[0].status).toBe('COMPLETED');
    expect(result[0].curItem).toBe('1605 | 328 | 25.09.2026');
  });

  it('кастомные statuses/page/size подставляются в query', async () => {
    apiFetchJsonMock.mockResolvedValue([]);
    await fetchSentHistory(['COMPLETED'], 2, 50);
    const [url] = apiFetchJsonMock.mock.calls[0] as [string];
    expect(url).toBe('/api/v1.0.0/notifications/sent-history?statuses=COMPLETED&page=2&size=50');
  });

  it('пустой список statuses — без параметра statuses', async () => {
    apiFetchJsonMock.mockResolvedValue([]);
    await fetchSentHistory([], 0, 10);
    const [url] = apiFetchJsonMock.mock.calls[0] as [string];
    expect(url).toBe('/api/v1.0.0/notifications/sent-history?page=0&size=10');
  });

  it('невалидный элемент ответа — ZodError', async () => {
    apiFetchJsonMock.mockResolvedValue([{ notificationId: 'oops' }]);
    await expect(fetchSentHistory()).rejects.toThrow();
  });
});

describe('fetchExecutorHistory', () => {
  it('URL executor-history с дефолтными параметрами', async () => {
    apiFetchJsonMock.mockResolvedValue([entry]);
    const result = await fetchExecutorHistory();
    const [url] = apiFetchJsonMock.mock.calls[0] as [string];
    expect(url).toBe(
      '/api/v1.0.0/notifications/executor-history?statuses=COMPLETED&statuses=CANCELLED&page=0&size=20'
    );
    expect(result[0].acceptedByName).toBe('Пётр');
  });
});
