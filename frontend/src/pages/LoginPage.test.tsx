// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';
import { AUTH_COPY } from '../config';

const mocks = vi.hoisted(() => ({
  loginUser: vi.fn(),
  login: vi.fn(),
}));

vi.mock('../api/auth', () => ({
  loginUser: mocks.loginUser,
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ login: mocks.login }),
}));

function renderLogin() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('LoginPage', () => {
  beforeEach(() => {
    mocks.loginUser.mockResolvedValue({
      userId: '12345',
      role: 'USER',
      accessToken: 'access',
      refreshToken: 'refresh',
      temporaryPassword: false,
    });
  });

  it('рендерит форму входа с полями табельного номера и пароля', () => {
    renderLogin();
    expect(screen.getByText(AUTH_COPY.title)).toBeInTheDocument();
    expect(screen.getByLabelText(AUTH_COPY.workerCodeLabel)).toBeInTheDocument();
    expect(screen.getByLabelText(AUTH_COPY.passwordLabel)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: AUTH_COPY.submit })).toBeInTheDocument();
  });

  it('submit с пустыми полями — показывает ошибки валидации, loginUser не вызван', async () => {
    const user = userEvent.setup();
    renderLogin();
    await user.click(screen.getByRole('button', { name: AUTH_COPY.submit }));
    expect(screen.getByText(AUTH_COPY.requiredWorkerCode)).toBeInTheDocument();
    expect(screen.getByText(AUTH_COPY.requiredPassword)).toBeInTheDocument();
    expect(mocks.loginUser).not.toHaveBeenCalled();
  });

  it('submit с заполненными полями — loginUser вызван с credentials, login с токенами', async () => {
    const user = userEvent.setup();
    renderLogin();
    await user.type(screen.getByLabelText(AUTH_COPY.workerCodeLabel), '12345');
    await user.type(screen.getByLabelText(AUTH_COPY.passwordLabel), 'secret');
    await user.click(screen.getByRole('button', { name: AUTH_COPY.submit }));

    expect(mocks.loginUser).toHaveBeenCalledWith({ workerCode: '12345', password: 'secret' });
    expect(mocks.login).toHaveBeenCalledWith('12345', 'USER', 'access', 'refresh');
  });

  it('ошибка 401 — показан текст «пользователь не найден»', async () => {
    const user = userEvent.setup();
    mocks.loginUser.mockRejectedValue(new Error('401|Unauthorized'));
    renderLogin();
    await user.type(screen.getByLabelText(AUTH_COPY.workerCodeLabel), '12345');
    await user.type(screen.getByLabelText(AUTH_COPY.passwordLabel), 'wrong');
    await user.click(screen.getByRole('button', { name: AUTH_COPY.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(AUTH_COPY.notFound);
    expect(mocks.login).not.toHaveBeenCalled();
  });

  it('ошибка 403 — показан текст «пользователь заблокирован»', async () => {
    const user = userEvent.setup();
    mocks.loginUser.mockRejectedValue(new Error('403|Forbidden'));
    renderLogin();
    await user.type(screen.getByLabelText(AUTH_COPY.workerCodeLabel), '12345');
    await user.type(screen.getByLabelText(AUTH_COPY.passwordLabel), 'secret');
    await user.click(screen.getByRole('button', { name: AUTH_COPY.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(AUTH_COPY.blocked);
  });

  it('сетевая ошибка (TypeError) — показан текст ошибки сети', async () => {
    const user = userEvent.setup();
    mocks.loginUser.mockRejectedValue(new TypeError('Failed to fetch'));
    renderLogin();
    await user.type(screen.getByLabelText(AUTH_COPY.workerCodeLabel), '12345');
    await user.type(screen.getByLabelText(AUTH_COPY.passwordLabel), 'secret');
    await user.click(screen.getByRole('button', { name: AUTH_COPY.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(AUTH_COPY.networkError);
  });
});
