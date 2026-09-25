// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LogsTab } from './LogsTab';
import type { DetailsContextValue } from '../../context/DetailsContext';
import type { DeviceError } from '../../types';

const mocks = vi.hoisted(() => ({
  useDetailsContext: vi.fn(),
}));

vi.mock('../../context/DetailsContext', () => ({
  useDetailsContext: mocks.useDetailsContext,
}));

function makeContext(over: Partial<DetailsContextValue>): DetailsContextValue {
  return {
    lineData: null,
    devicesData: null,
    queueData: null,
    errorsData: null,
    devicesTopology: null,
    devicesLoading: false,
    topologyError: null,
    unitSignal: 'connected',
    pageError: null,
    ...over,
  } as DetailsContextValue;
}

const inactiveError: DeviceError = {
  objectName: 'Printer11',
  propertyDesc: 'Error',
  value: '0',
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('LogsTab', () => {
  it('нет активных ошибок — показан текст, без иконки, заголовок чёрный', () => {
    mocks.useDetailsContext.mockReturnValue(
      makeContext({ errorsData: { deviceErrors: [inactiveError] } })
    );
    const { container } = render(<LogsTab />);

    const emptyText = screen.getByText('Нет активных ошибок');
    expect(emptyText).toBeInTheDocument();
    expect(emptyText.querySelector('img')).toBeNull();
    expect(screen.queryByRole('img')).toBeNull();

    const title = screen.getByText('Активные ошибки');
    expect(title).toBeInTheDocument();
    expect(getComputedStyle(title).color).toBe('rgb(26, 28, 30)');
    expect(getComputedStyle(title).color).not.toBe('rgb(234, 67, 53)');
    expect(container.querySelector('.error-item')).toBeNull();
  });

  it('одна активная ошибка — время в формате «дд.мм.гггг ч:мм:сс», без кода DevNNNFail, objectName и description видны', () => {
    mocks.useDetailsContext.mockReturnValue(
      makeContext({
        errorsData: {
          deviceErrors: [
            {
              objectName: 'Printer11',
              propertyDesc: 'Dev041Fail',
              value: '1',
              description: 'Замятие этикетки',
              occurredAt: '2026-09-25T05:57:00',
            },
          ],
        },
      })
    );
    const { container } = render(<LogsTab />);

    expect(screen.getByText('Printer11')).toBeInTheDocument();
    expect(screen.getByText('25.09.2026 5:57:00')).toBeInTheDocument();
    expect(screen.getByText('Замятие этикетки')).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/Dev\d+Fail/);
  });

  it('нераспарсенное occurredAt выводится как есть', () => {
    mocks.useDetailsContext.mockReturnValue(
      makeContext({
        errorsData: {
          deviceErrors: [
            {
              objectName: 'Line',
              propertyDesc: 'Error',
              value: '1',
              occurredAt: 'не-дата',
            },
          ],
        },
      })
    );
    render(<LogsTab />);
    expect(screen.getByText('не-дата')).toBeInTheDocument();
  });

  it('есть ошибка страницы и нет данных — показано сообщение об ошибке', () => {
    mocks.useDetailsContext.mockReturnValue(
      makeContext({
        unitSignal: 'error',
        errorsData: null,
        pageError: {
          code: 'server_error',
          message: 'Сервер вернул ошибку',
          source: 'ws/unit',
          severity: 'transient',
          retryable: true,
          raw: '',
        },
      })
    );
    render(<LogsTab />);
    expect(screen.getByText('Ошибка на сервере')).toBeInTheDocument();
    expect(screen.queryByText('Нет активных ошибок')).not.toBeInTheDocument();
  });
});
