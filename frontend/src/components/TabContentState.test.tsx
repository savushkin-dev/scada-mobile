// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { TabContentState } from './TabContentState';
import type { AppError } from '../errors/AppError';

const testError: AppError = {
  code: 'server_error',
  message: 'Сервер вернул ошибку',
  source: 'topology/units',
  severity: 'degraded',
  retryable: true,
  raw: '',
};

afterEach(cleanup);

describe('TabContentState', () => {
  it('isLoading — рендерит skeleton вместо children', () => {
    render(
      <TabContentState isLoading error={null} skeleton={<div>скелетон</div>}>
        <div>контент</div>
      </TabContentState>
    );
    expect(screen.getByText('скелетон')).toBeInTheDocument();
    expect(screen.queryByText('контент')).not.toBeInTheDocument();
  });

  it('isLoading имеет приоритет над error', () => {
    render(
      <TabContentState isLoading error={testError} skeleton={<div>скелетон</div>}>
        <div>контент</div>
      </TabContentState>
    );
    expect(screen.getByText('скелетон')).toBeInTheDocument();
    expect(screen.queryByText('контент')).not.toBeInTheDocument();
  });

  it('error — показывает пользовательское сообщение об ошибке', () => {
    render(
      <TabContentState isLoading={false} error={testError} skeleton={<div>скелетон</div>}>
        <div>контент</div>
      </TabContentState>
    );
    expect(screen.queryByText('скелетон')).not.toBeInTheDocument();
    expect(screen.queryByText('контент')).not.toBeInTheDocument();
    expect(screen.getByText(/ошибк/i)).toBeInTheDocument();
  });

  it('нет loading и error — рендерит children', () => {
    render(
      <TabContentState isLoading={false} error={null} skeleton={<div>скелетон</div>}>
        <div>контент</div>
      </TabContentState>
    );
    expect(screen.getByText('контент')).toBeInTheDocument();
    expect(screen.queryByText('скелетон')).not.toBeInTheDocument();
  });
});
