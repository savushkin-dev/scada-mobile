// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { UnitCard } from './UnitCard';
import type { AlertData, Unit } from '../types';

const mocks = vi.hoisted(() => ({
  useAuth: vi.fn(),
  useAccessControl: vi.fn(),
  useLastBatch: vi.fn(),
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: mocks.useAuth,
}));

vi.mock('../context/AccessControlContext', () => ({
  useAccessControl: mocks.useAccessControl,
}));

vi.mock('../hooks/useLastBatch', () => ({
  useLastBatch: mocks.useLastBatch,
}));

function makeUnit(over: Partial<Unit> = {}): Unit {
  return {
    id: '1',
    workshopId: 5,
    unit: 'Автомат 1',
    event: 'Работает',
    statusReady: true,
    cameraRead: null,
    cameraUnread: null,
    ...over,
  };
}

function makeAlert(over: Partial<AlertData> = {}): AlertData {
  return {
    unitName: 'Автомат 1',
    errors: [{ device: 'Printer11', code: 0, message: 'Нет этикетки' }],
    timestamp: '2026-09-25T06:00:00',
    workshopId: 5,
    ...over,
  };
}

function renderCard(unit: Unit, alerts: Map<string, AlertData>) {
  return render(<UnitCard unit={unit} alerts={alerts} onClick={() => {}} />);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('UnitCard', () => {
  it('автомат с активной ошибкой — класс status-critical и текст ошибки на табло', () => {
    mocks.useAuth.mockReturnValue({ userId: 'u1' });
    mocks.useAccessControl.mockReturnValue({ isAssignedUnit: () => false });
    mocks.useLastBatch.mockReturnValue({
      sending: false,
      sent: false,
      result: 'idle',
      sendLastBatch: vi.fn(),
      reset: vi.fn(),
    });

    renderCard(makeUnit(), new Map([['1', makeAlert()]]));

    const card = screen.getByRole('button');
    expect(card.className).toContain('status-critical');
    expect(screen.getByText('Printer11')).toBeInTheDocument();
    expect(screen.getByText('Нет этикетки')).toBeInTheDocument();
  });

  it('автомат без ошибок с данными — класс status-normal', () => {
    mocks.useAuth.mockReturnValue({ userId: 'u1' });
    mocks.useAccessControl.mockReturnValue({ isAssignedUnit: () => false });
    mocks.useLastBatch.mockReturnValue({
      sending: false,
      sent: false,
      result: 'idle',
      sendLastBatch: vi.fn(),
      reset: vi.fn(),
    });

    renderCard(makeUnit(), new Map());

    const card = screen.getByRole('button');
    expect(card.className).toContain('status-normal');
    expect(screen.getByText('Работает')).toBeInTheDocument();
  });

  it('автомат без данных («Нет данных») — серый статус, карточка неактивна', () => {
    mocks.useAuth.mockReturnValue({ userId: 'u1' });
    mocks.useAccessControl.mockReturnValue({ isAssignedUnit: () => false });
    mocks.useLastBatch.mockReturnValue({
      sending: false,
      sent: false,
      result: 'idle',
      sendLastBatch: vi.fn(),
      reset: vi.fn(),
    });

    renderCard(makeUnit({ event: 'Нет данных' }), new Map());

    const card = screen.getByText('Нет данных');
    expect(card.closest('.card')?.className).toContain('status-pending');
    expect(card.closest('.card')?.className).toContain('card-static');
    expect(card.closest('.card')).toHaveAttribute('aria-disabled', 'true');
  });

  it('статус ещё не получен (statusReady=false) — серый статус', () => {
    mocks.useAuth.mockReturnValue({ userId: 'u1' });
    mocks.useAccessControl.mockReturnValue({ isAssignedUnit: () => false });
    mocks.useLastBatch.mockReturnValue({
      sending: false,
      sent: false,
      result: 'idle',
      sendLastBatch: vi.fn(),
      reset: vi.fn(),
    });

    renderCard(makeUnit({ statusReady: false }), new Map());

    const card = screen.getByRole('button');
    expect(card.className).toContain('status-pending');
  });

  it('активное уведомление — показан колокольчик с title', () => {
    mocks.useAuth.mockReturnValue({ userId: 'u1' });
    mocks.useAccessControl.mockReturnValue({ isAssignedUnit: () => false });
    mocks.useLastBatch.mockReturnValue({
      sending: false,
      sent: false,
      result: 'idle',
      sendLastBatch: vi.fn(),
      reset: vi.fn(),
    });

    render(
      <UnitCard
        unit={makeUnit()}
        alerts={new Map()}
        notifications={
          new Map([
            [
              '1',
              {
                unitName: 'Автомат 1',
                creatorId: 'u2',
                creatorName: null,
                timestamp: null,
                eventType: null,
              },
            ],
          ])
        }
        onClick={() => {}}
      />
    );

    expect(screen.getByTitle('Активное уведомление')).toBeInTheDocument();
  });
});
