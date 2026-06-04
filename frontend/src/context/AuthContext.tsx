import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { ReactNode } from 'react';
import {
  clearAllAuthData,
  getAccessToken,
  getInitialUserId,
  getStoredRole,
  setStoredRole,
  setStoredUserId,
  setTokens,
  subscribeToAuthSync,
} from '../auth/session';
import { isTokenExpired, getTokenTimeRemaining } from '../auth/token';
import { refreshAccessToken } from '../api/auth';

interface AuthContextValue {
  userId: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isVerifying: boolean;
  login: (userId: string, role: string, accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Интервал проверки токена — каждые 60 секунд. */
const TOKEN_CHECK_INTERVAL_MS = 60_000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(() => getInitialUserId());
  const [role, setRole] = useState<string | null>(() => getStoredRole());
  const [isVerifying, setIsVerifying] = useState(true);
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── Initial verification ───────────────────────────────────────────────
  useEffect(() => {
    const token = getAccessToken();
    const storedUserId = getInitialUserId();

    if (!token || !storedUserId) {
      // Нет токена или userId — точно не аутентифицирован
      if (token || storedUserId) {
        clearAllAuthData();
      }
      setUserId(null);
      setRole(null);
      setIsVerifying(false);
      return;
    }

    if (isTokenExpired(token)) {
      // Токен истёк — пробуем refresh
      refreshAccessToken()
        .then((newToken) => {
          if (!newToken) {
            clearAllAuthData();
            setUserId(null);
            setRole(null);
          }
        })
        .catch(() => {
          clearAllAuthData();
          setUserId(null);
          setRole(null);
        })
        .finally(() => {
          setIsVerifying(false);
        });
    } else {
      setIsVerifying(false);
    }
  }, []);

  // ── Multi-tab synchronization ──────────────────────────────────────────
  useEffect(() => {
    const unsubscribe = subscribeToAuthSync(
      () => {
        // Logout в другой вкладке — сбрасываем состояние
        if (refreshTimerRef.current) {
          clearTimeout(refreshTimerRef.current);
          refreshTimerRef.current = null;
        }
        setUserId(null);
        setRole(null);
        setIsVerifying(false);
      },
      () => {
        // Tokens обновились в другой вкладке — перечитываем userId/role
        const updatedUserId = getInitialUserId();
        const updatedRole = getStoredRole();
        setUserId(updatedUserId);
        setRole(updatedRole);
      }
    );
    return unsubscribe;
  }, []);

  // ── Proactive refresh timer ────────────────────────────────────────────
  useEffect(() => {
    if (!userId) return;

    function scheduleNextCheck(): void {
      const token = getAccessToken();
      if (!token) return;

      const remaining = getTokenTimeRemaining(token);
      if (remaining <= 0) {
        // Токен уже истёк — refresh
        void refreshAccessToken().then((newToken) => {
          if (!newToken) {
            clearAllAuthData();
            setUserId(null);
            setRole(null);
          }
        });
        return;
      }

      // Планируем проверку: либо через стандартный интервал,
      // либо за 10 секунд до истечения (чтобы успеть обновить)
      const delay = Math.min(TOKEN_CHECK_INTERVAL_MS, Math.max(remaining * 1000 - 10_000, 5_000));
      refreshTimerRef.current = setTimeout(() => {
        void refreshAccessToken().then((newToken) => {
          if (!newToken) {
            clearAllAuthData();
            setUserId(null);
            setRole(null);
            return;
          }
          scheduleNextCheck();
        });
      }, delay);
    }

    scheduleNextCheck();

    return () => {
      if (refreshTimerRef.current) {
        clearTimeout(refreshTimerRef.current);
        refreshTimerRef.current = null;
      }
    };
  }, [userId]);

  const login = useCallback(
    (nextUserId: string, nextRole: string, accessToken: string, refreshToken: string) => {
      const normalized = nextUserId.trim();
      if (!normalized) return;
      setUserId(normalized);
      setStoredUserId(normalized);
      setRole(nextRole);
      setStoredRole(nextRole);
      setTokens(accessToken, refreshToken);
      setIsVerifying(false);
    },
    []
  );

  const logout = useCallback(() => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = null;
    }
    setUserId(null);
    setRole(null);
    setIsVerifying(false);
    clearAllAuthData();
  }, []);

  const accessToken = getAccessToken();
  const isTokenValid = Boolean(accessToken) && !isTokenExpired(accessToken);
  const isAuthenticated = isTokenValid && Boolean(userId);
  const isAdmin = role === 'ADMIN';

  const value = useMemo(
    () => ({
      userId,
      role,
      isAuthenticated,
      isAdmin,
      isVerifying,
      login,
      logout,
    }),
    [userId, role, isAuthenticated, isAdmin, isVerifying, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
