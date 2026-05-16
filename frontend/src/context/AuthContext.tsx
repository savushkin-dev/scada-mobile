import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  clearAllAuthData,
  getAccessToken,
  getInitialUserId,
  setStoredUserId,
  setTokens,
} from '../auth/session';

interface AuthContextValue {
  userId: string | null;
  isAuthenticated: boolean;
  login: (userId: string, accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(() => getInitialUserId());

  const login = useCallback((nextUserId: string, accessToken: string, refreshToken: string) => {
    const normalized = nextUserId.trim();
    if (!normalized) return;
    setUserId(normalized);
    setStoredUserId(normalized);
    setTokens(accessToken, refreshToken);
  }, []);

  const logout = useCallback(() => {
    setUserId(null);
    clearAllAuthData();
  }, []);

  const isAuthenticated = Boolean(getAccessToken()) || Boolean(userId);

  const value = useMemo(
    () => ({
      userId,
      isAuthenticated,
      login,
      logout,
    }),
    [userId, isAuthenticated, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
