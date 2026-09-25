// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DesktopDataTable } from './DesktopDataTable';

const mocks = vi.hoisted(() => ({
  useListContext: vi.fn(),
  useTableFilters: vi.fn(),
}));

vi.mock('react-admin', () => ({
  useListContext: mocks.useListContext,
}));

vi.mock('../filters/TableFilterContext', () => ({
  useTableFilters: mocks.useTableFilters,
}));

interface Row {
  id: number;
  name: string;
  note: string;
}

const rows: Row[] = [
  { id: 1, name: 'Альфа', note: 'первая' },
  { id: 2, name: 'Бета', note: 'вторая' },
];

const columns = [
  { key: 'name', header: 'Имя', sortKey: 'name', render: (r: Row) => r.name },
  { key: 'note', header: 'Заметка', render: (r: Row) => r.note },
];

function renderTable(
  sort: { field: string; order: 'ASC' | 'DESC' } = { field: 'id', order: 'ASC' }
) {
  const setSort = vi.fn();
  mocks.useListContext.mockReturnValue({ sort, setSort });
  mocks.useTableFilters.mockReturnValue(null);
  const view = render(
    <DesktopDataTable columns={columns} records={rows} keyExtractor={(r) => r.id} />
  );
  return { ...view, setSort };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('DesktopDataTable', () => {
  it('рендерит колонки и строки из records', () => {
    renderTable();
    expect(screen.getByText('Имя')).toBeInTheDocument();
    expect(screen.getByText('Заметка')).toBeInTheDocument();
    expect(screen.getByText('Альфа')).toBeInTheDocument();
    expect(screen.getByText('вторая')).toBeInTheDocument();
  });

  it('клик по сортируемой колонке вызывает setSort с ASC', async () => {
    const user = userEvent.setup();
    const { setSort } = renderTable();
    await user.click(screen.getByRole('button', { name: /Имя/ }));
    expect(setSort).toHaveBeenCalledWith({ field: 'name', order: 'ASC' });
  });

  it('повторный клик при активной ASC сортировке переключает на DESC', async () => {
    const user = userEvent.setup();
    const { setSort, rerender } = renderTable({ field: 'name', order: 'ASC' });
    await user.click(screen.getByRole('button', { name: /Имя/ }));
    expect(setSort).toHaveBeenCalledWith({ field: 'name', order: 'DESC' });
    void rerender;
  });

  it('при sort.field === sortKey и order DESC активная стрелка отмечена цветом сортировки', () => {
    const { container } = renderTable({ field: 'name', order: 'DESC' });
    const headerButton = screen.getByRole('button', { name: /Имя/ });
    expect(headerButton.querySelector('[class*="4285f4"]')).not.toBeNull();
    expect(container.querySelector('[class*="e0e2e5"]')).toBeNull();
  });

  it('колонка без sortKey не кликабельна и не вызывает setSort', async () => {
    const user = userEvent.setup();
    const { setSort, container } = renderTable();
    expect(screen.queryByRole('button', { name: /Заметка/ })).toBeNull();
    const th = screen.getByText('Заметка').closest('th');
    await user.click(th as HTMLElement);
    expect(setSort).not.toHaveBeenCalled();
    expect(container.querySelector('[class*="4285f4"]')).toBeNull();
  });
});
