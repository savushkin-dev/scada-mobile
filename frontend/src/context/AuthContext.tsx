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
import { isTokenExpired, isTokenFullyExpired, getTokenTimeRemaining } from '../auth/token';
import { refreshAccessToken } from '../api/auth';
import { NetworkUnavailableError, ServerUnavailableError } from '../errors/AppError';
import { ServerUnavailablePage } from '../pages/ServerUnavailablePage';

interface AuthContextValue {
  userId: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isVerifying: boolean;
  isServerUnavailable: boolean;
  login: (userId: string, role: string, accessToken: string, refreshToken: string) => void;
  logout: () => void;
  checkServerAvailability: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Интервал проверки токена — каждые 60 секунд. */
const TOKEN_CHECK_INTERVAL_MS = 60_000;

/**
 * Определяет, нужно ли считать ошибку refresh признаком недоступности сервера.
 * При таких ошибках токены не стираются, а приложение показывает заглушку.
 */
function isServerUnavailableError(error: unknown): boolean {
  return error instanceof NetworkUnavailableError || error instanceof ServerUnavailableError;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(() => getInitialUserId());
  const [role, setRole] = useState<string | null>(() => getStoredRole());
  const [isVerifying, setIsVerifying] = useState(true);
  const [isServerUnavailable, setIsServerUnavailable] = useState(false);
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleSessionExpired = useCallback(() => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = null;
    }
    clearAllAuthData();
    setUserId(null);
    setRole(null);
  }, []);

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
        .then(() => {
          // Refresh успешен — сервер доступен
          setIsServerUnavailable(false);
        })
        .catch((error) => {
          if (isServerUnavailableError(error)) {
            // Сервер недоступен — токены оставляем, показываем заглушку
            setIsServerUnavailable(true);
            return;
          }
          // 401/403 или другая ошибка аутентификации — logout
          handleSessionExpired();
        })
        .finally(() => {
          setIsVerifying(false);
        });
    } else {
      setIsVerifying(false);
    }
  }, [handleSessionExpired]);

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
        setIsServerUnavailable(false);
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
        void refreshAccessToken()
          .then(() => {
            setIsServerUnavailable(false);
            scheduleNextCheck();
          })
          .catch((error) => {
            if (isServerUnavailableError(error)) {
              setIsServerUnavailable(true);
              return;
            }
            handleSessionExpired();
          });
        return;
      }

      // Планируем проверку: либо через стандартный интервал,
      // либо за 10 секунд до истечения (чтобы успеть обновить)
      const delay = Math.min(TOKEN_CHECK_INTERVAL_MS, Math.max(remaining * 1000 - 10_000, 5_000));
      refreshTimerRef.current = setTimeout(() => {
        void refreshAccessToken()
          .then(() => {
            setIsServerUnavailable(false);
            scheduleNextCheck();
          })
          .catch((error) => {
            if (isServerUnavailableError(error)) {
              setIsServerUnavailable(true);
              return;
            }
            handleSessionExpired();
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
  }, [userId, handleSessionExpired]);

  const login = useCallback(
    (nextUserId: string, nextRole: string, accessToken: string, refreshToken: string) => {
      const normalized = nextUserId.trim();
      if (!normalized) return;
      setUserId(normalized);
      setStoredUserId(normalized);
      setRole(nextRole);
      setStoredRole(nextRole);
      setTokens(accessToken, refreshToken);
      setIsServerUnavailable(false);
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
    setIsServerUnavailable(false);
    setIsVerifying(false);
    clearAllAuthData();
  }, []);

  /**
   * Повторная проверка доступности сервера.
   * Вызывается из заглушки "сервер недоступен" по кнопке "Повторить".
   */
  const checkServerAvailability = useCallback(() => {
    const token = getAccessToken();
    if (!token) {
      handleSessionExpired();
      return;
    }

    if (isTokenExpired(token)) {
      refreshAccessToken()
        .then(() => {
          setIsServerUnavailable(false);
        })
        .catch((error) => {
          if (isServerUnavailableError(error)) {
            setIsServerUnavailable(true);
            return;
          }
          handleSessionExpired();
        });
    } else {
      // Токен ещё валиден — сервер снова доступен
      setIsServerUnavailable(false);
    }
  }, [handleSessionExpired]);

  const accessToken = getAccessToken();
  const isTokenValid = Boolean(accessToken) && !isTokenFullyExpired(accessToken);
  const isAuthenticated = isTokenValid && Boolean(userId);
  const isAdmin = role === 'ADMIN';

  const value = useMemo(
    () => ({
      userId,
      role,
      isAuthenticated,
      isAdmin,
      isVerifying,
      isServerUnavailable,
      login,
      logout,
      checkServerAvailability,
    }),
    [
      userId,
      role,
      isAuthenticated,
      isAdmin,
      isVerifying,
      isServerUnavailable,
      login,
      logout,
      checkServerAvailability,
    ]
  );

  if (isServerUnavailable) {
    return <ServerUnavailablePage onRetry={checkServerAvailability} />;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
