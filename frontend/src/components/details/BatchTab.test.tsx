// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BatchTab } from './BatchTab';
import type { DetailsContextValue } from '../../context/DetailsContext';
import type { LineStatusPayload } from '../../types';

const mocks = vi.hoisted(() => ({
  useDetailsContext: vi.fn(),
}));

vi.mock('../../context/DetailsContext', () => ({
  useDetailsContext: mocks.useDetailsContext,
}));

function makeContext(lineData: LineStatusPayload | null): DetailsContextValue {
  return {
    lineData,
    devicesData: null,
    queueData: null,
    errorsData: null,
    devicesTopology: null,
    devicesLoading: false,
    topologyError: null,
    unitSignal: 'connected',
    pageError: null,
  } as DetailsContextValue;
}

const baseLine: LineStatusPayload = {
  description: null,
  ean13: '4607025392110',
  batchNumber: 'БН-123',
  dateProduced: '2026-09-24',
  dateExpiration: '2026-10-24',
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('BatchTab', () => {
  it('region=null в дополнительных полях — отображается «—»', async () => {
    const user = userEvent.setup();
    mocks.useDetailsContext.mockReturnValue(makeContext({ ...baseLine, region: null }));
    render(<BatchTab />);

    await user.click(screen.getByRole('button', { name: /Показать все свойства/ }));

    const regionRow = screen.getByText('Регион').closest('.kv-row');
    expect(regionRow).not.toBeNull();
    expect(within(regionRow as HTMLElement).getByText('—')).toBeInTheDocument();
  });

  it('region=пустая строка — отображается «—»', async () => {
    const user = userEvent.setup();
    mocks.useDetailsContext.mockReturnValue(makeContext({ ...baseLine, region: '   ' }));
    render(<BatchTab />);

    await user.click(screen.getByRole('button', { name: /Показать все свойства/ }));

    const regionRow = screen.getByText('Регион').closest('.kv-row');
    expect(within(regionRow as HTMLElement).getByText('—')).toBeInTheDocument();
  });

  it('непустой region — отображается значение', async () => {
    const user = userEvent.setup();
    mocks.useDetailsContext.mockReturnValue(makeContext({ ...baseLine, region: 'СЗФО' }));
    render(<BatchTab />);

    await user.click(screen.getByRole('button', { name: /Показать все свойства/ }));

    const regionRow = screen.getByText('Регион').closest('.kv-row');
    expect(within(regionRow as HTMLElement).getByText('СЗФО')).toBeInTheDocument();
  });

  it('пустое описание в основных полях — отображается «—»', () => {
    mocks.useDetailsContext.mockReturnValue(makeContext(baseLine));
    render(<BatchTab />);

    const descRow = screen.getByText('Описание').closest('.kv-row');
    expect(within(descRow as HTMLElement).getByText('—')).toBeInTheDocument();
  });

  it('пока нет данных и сигнал idle — показан skeleton вместо контента', () => {
    const ctx = makeContext(null);
    ctx.unitSignal = 'idle';
    mocks.useDetailsContext.mockReturnValue(ctx);
    const { container } = render(<BatchTab />);
    expect(screen.queryByText('Текущая партия')).not.toBeInTheDocument();
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeNull();
  });
});
