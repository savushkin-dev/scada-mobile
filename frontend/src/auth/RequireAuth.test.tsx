// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RequireAuth } from './RequireAuth';
import { RequireAdmin } from './RequireAdmin';

const mocks = vi.hoisted(() => ({
  useAuth: vi.fn(),
  getAccessToken: vi.fn(),
  isTemporaryPasswordToken: vi.fn(),
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: mocks.useAuth,
}));

vi.mock('../auth/session', () => ({
  getAccessToken: mocks.getAccessToken,
}));

vi.mock('../auth/token', () => ({
  isTemporaryPasswordToken: mocks.isTemporaryPasswordToken,
}));

function renderAdmin(childrenPath = '/admin') {
  return render(
    <MemoryRouter initialEntries={[childrenPath]}>
      <Routes>
        <Route
          path="/admin"
          element={
            <RequireAdmin>
              <div>админ-контент</div>
            </RequireAdmin>
          }
        />
        <Route path="/login" element={<div>страница входа</div>} />
        <Route path="/" element={<div>главная</div>} />
      </Routes>
    </MemoryRouter>
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('RequireAuth', () => {
  beforeEach(() => {
    mocks.useAuth.mockReturnValue({
      isAuthenticated: true,
      isVerifying: false,
    });
    mocks.getAccessToken.mockReturnValue('token');
    mocks.isTemporaryPasswordToken.mockReturnValue(false);
  });

  it('пока идёт проверка токена — показывает загрузчик, не children', () => {
    mocks.useAuth.mockReturnValue({ isAuthenticated: false, isVerifying: true });
    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>защищённый контент</div>} />
          </Route>
          <Route path="/login" element={<div>страница входа</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.queryByText('защищённый контент')).not.toBeInTheDocument();
    expect(screen.queryByText('страница входа')).not.toBeInTheDocument();
    expect(document.querySelector('.animate-spin')).not.toBeNull();
  });

  it('нет аутентификации — редирект на /login', () => {
    mocks.useAuth.mockReturnValue({ isAuthenticated: false, isVerifying: false });
    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>защищённый контент</div>} />
          </Route>
          <Route path="/login" element={<div>страница входа</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('страница входа')).toBeInTheDocument();
    expect(screen.queryByText('защищённый контент')).not.toBeInTheDocument();
  });

  it('временный пароль на другом маршруте — редирект на /change-password', () => {
    mocks.isTemporaryPasswordToken.mockReturnValue(true);
    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>защищённый контент</div>} />
          </Route>
          <Route path="/login" element={<div>страница входа</div>} />
          <Route path="/change-password" element={<div>смена пароля</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('смена пароля')).toBeInTheDocument();
    expect(screen.queryByText('защищённый контент')).not.toBeInTheDocument();
  });

  it('временный пароль на /change-password — редиректа нет', () => {
    mocks.isTemporaryPasswordToken.mockReturnValue(true);
    render(
      <MemoryRouter initialEntries={['/change-password']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/change-password" element={<div>форма смены пароля</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('форма смены пароля')).toBeInTheDocument();
  });

  it('аутентифицирован — рендерится вложенный маршрут', () => {
    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>защищённый контент</div>} />
          </Route>
          <Route path="/login" element={<div>страница входа</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('защищённый контент')).toBeInTheDocument();
  });
});

describe('RequireAdmin', () => {
  beforeEach(() => {
    mocks.useAuth.mockReturnValue({
      isAuthenticated: true,
      isAdmin: true,
      isVerifying: false,
    });
  });

  it('пока идёт проверка — загрузчик вместо children', () => {
    mocks.useAuth.mockReturnValue({ isAuthenticated: false, isAdmin: false, isVerifying: true });
    renderAdmin();
    expect(screen.queryByText('админ-контент')).not.toBeInTheDocument();
    expect(document.querySelector('.animate-spin')).not.toBeNull();
  });

  it('не аутентифицирован — редирект на /login', () => {
    mocks.useAuth.mockReturnValue({ isAuthenticated: false, isAdmin: false, isVerifying: false });
    renderAdmin();
    expect(screen.getByText('страница входа')).toBeInTheDocument();
    expect(screen.queryByText('админ-контент')).not.toBeInTheDocument();
  });

  it('нет роли ADMIN — редирект на главную', () => {
    mocks.useAuth.mockReturnValue({ isAuthenticated: true, isAdmin: false, isVerifying: false });
    renderAdmin();
    expect(screen.getByText('главная')).toBeInTheDocument();
    expect(screen.queryByText('админ-контент')).not.toBeInTheDocument();
  });

  it('админ аутентифицирован — рендерится children', () => {
    renderAdmin();
    expect(screen.getByText('админ-контент')).toBeInTheDocument();
  });
});
