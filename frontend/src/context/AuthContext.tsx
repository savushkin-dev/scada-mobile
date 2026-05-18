import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  clearAllAuthData,
  getAccessToken,
  getInitialUserId,
  getStoredRole,
  setStoredRole,
  setStoredUserId,
  setTokens,
} from '../auth/session';

interface AuthContextValue {
  userId: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (userId: string, role: string, accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(() => getInitialUserId());
  const [role, setRole] = useState<string | null>(() => getStoredRole());

  const login = useCallback(
    (nextUserId: string, nextRole: string, accessToken: string, refreshToken: string) => {
      const normalized = nextUserId.trim();
      if (!normalized) return;
      setUserId(normalized);
      setStoredUserId(normalized);
      setRole(nextRole);
      setStoredRole(nextRole);
      setTokens(accessToken, refreshToken);
    },
    []
  );

  const logout = useCallback(() => {
    setUserId(null);
    setRole(null);
    clearAllAuthData();
  }, []);

  const isAuthenticated = Boolean(getAccessToken()) || Boolean(userId);
  const isAdmin = role === 'ADMIN';

  const value = useMemo(
    () => ({
      userId,
      role,
      isAuthenticated,
      isAdmin,
      login,
      logout,
    }),
    [userId, role, isAuthenticated, isAdmin, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
