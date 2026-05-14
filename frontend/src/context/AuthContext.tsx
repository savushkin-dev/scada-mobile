import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { clearStoredUserId, getInitialUserId, setStoredUserId } from '../auth/session';

interface AuthContextValue {
  userId: string | null;
  isAuthenticated: boolean;
  login: (userId: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(() => getInitialUserId());

  const login = useCallback((nextUserId: string) => {
    const normalized = nextUserId.trim();
    if (!normalized) return;
    setUserId(normalized);
    setStoredUserId(normalized);
  }, []);

  const logout = useCallback(() => {
    setUserId(null);
    clearStoredUserId();
  }, []);

  const value = useMemo(
    () => ({
      userId,
      isAuthenticated: Boolean(userId),
      login,
      logout,
    }),
    [userId, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
