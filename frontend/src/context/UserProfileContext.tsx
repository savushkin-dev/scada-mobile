import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { fetchNotificationSettings, fetchUserProfile } from '../api/profile';
import { useAuth } from './AuthContext';
import { classifyError } from '../errors/classifyError';
import type { AppError } from '../errors/AppError';
import type {
  ChangeAction,
  EmployeePayload,
  NotificationSetting,
  UnitPayload,
  UserAssignmentsMessage,
  UserNotificationSettingsPayload,
  UserProfile,
} from '../types';

type LoadStatus = 'idle' | 'loading' | 'ready' | 'error';

interface UserProfileContextValue {
  profile: UserProfile | null;
  profileStatus: LoadStatus;
  profileError: AppError | null;
  /** null — настройки ещё не загружались (или скрыты для админа). */
  settings: NotificationSetting[] | null;
  settingsStatus: LoadStatus;
  settingsError: AppError | null;
  refreshProfile: () => Promise<void>;
  refreshSettings: () => Promise<void>;
  /** Локальный (оптимистичный) патч строки настроек — используется тоглами ProfilePage. */
  applyLocalSetting: (updated: NotificationSetting) => void;
  /** EMPLOYEE_CHANGED для текущего пользователя — патч профиля без REST-запроса. */
  applyOwnEmployeeChange: (payload: EmployeePayload) => void;
  /** USER_ASSIGNMENTS — полный актуальный список закреплённых автоматов. */
  applyAssignmentsFromWs: (msg: UserAssignmentsMessage) => void;
  /** UNIT_CHANGED — создание/переименование/удаление автомата в профиле и настройках. */
  applyUnitTopologyChange: (payload: UnitPayload, action: ChangeAction) => void;
  /** USER_NOTIFICATION_SETTINGS_CHANGED для текущего пользователя. */
  applyOwnSettingsChange: (payload: UserNotificationSettingsPayload, action: ChangeAction) => void;
}

const UserProfileContext = createContext<UserProfileContextValue | null>(null);

/**
 * Живое состояние профиля текущего пользователя и его настроек уведомлений.
 *
 * REST используется только для первоначальной загрузки; дальнейшие изменения
 * (админ переназначил автоматы, переименовал сотрудника, поменял настройки и т.п.)
 * приходят по /ws/live и применяются здесь без перезагрузки страницы —
 * см. обработчики в RootLayout.
 */
export function UserProfileProvider({ children }: { children: ReactNode }) {
  const { userId, role } = useAuth();
  const isAdmin = role === 'ADMIN';

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileStatus, setProfileStatus] = useState<LoadStatus>('idle');
  const [profileError, setProfileError] = useState<AppError | null>(null);

  const [settings, setSettings] = useState<NotificationSetting[] | null>(null);
  const [settingsStatus, setSettingsStatus] = useState<LoadStatus>('idle');
  const [settingsError, setSettingsError] = useState<AppError | null>(null);

  const refreshProfile = useCallback(
    async (signal?: AbortSignal) => {
      if (!userId) return;
      try {
        const data = await fetchUserProfile(signal);
        setProfile(data);
        setProfileStatus('ready');
        setProfileError(null);
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') return;
        setProfileStatus((prev) => (prev === 'ready' ? prev : 'error'));
        setProfileError(classifyError(error, 'profile'));
      }
    },
    [userId]
  );

  const refreshSettings = useCallback(
    async (signal?: AbortSignal) => {
      if (!userId || isAdmin) return;
      try {
        const data = await fetchNotificationSettings(signal);
        setSettings(data);
        setSettingsStatus('ready');
        setSettingsError(null);
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') return;
        setSettingsStatus((prev) => (prev === 'ready' ? prev : 'error'));
        setSettingsError(classifyError(error, 'notification-settings'));
      }
    },
    [userId, isAdmin]
  );

  // Первоначальная загрузка (REST) — дальше состояние живёт на WS-событиях.
  useEffect(() => {
    if (!userId) {
      setProfile(null);
      setProfileStatus('idle');
      setProfileError(null);
      setSettings(null);
      setSettingsStatus('idle');
      setSettingsError(null);
      return;
    }

    const profileController = new AbortController();
    const settingsController = new AbortController();

    setProfileStatus('loading');
    void refreshProfile(profileController.signal);

    // Администратору блок настроек уведомлений не нужен (issue #38) — не грузим.
    if (isAdmin) {
      setSettings(null);
      setSettingsStatus('idle');
    } else {
      setSettingsStatus('loading');
      void refreshSettings(settingsController.signal);
    }

    return () => {
      profileController.abort();
      settingsController.abort();
    };
  }, [userId, isAdmin, refreshProfile, refreshSettings]);

  const applyLocalSetting = useCallback((updated: NotificationSetting) => {
    setSettings((prev) =>
      prev == null ? prev : prev.map((item) => (item.unitId === updated.unitId ? updated : item))
    );
  }, []);

  const applyOwnEmployeeChange = useCallback((payload: EmployeePayload) => {
    setProfile((prev) =>
      prev == null
        ? prev
        : {
            ...prev,
            fullName: payload.fullName ?? prev.fullName,
            workerCode: payload.code ?? prev.workerCode,
            role: payload.roleName ?? prev.role,
          }
    );
  }, []);

  const applyAssignmentsFromWs = useCallback((msg: UserAssignmentsMessage) => {
    setProfile((prev) =>
      prev == null
        ? prev
        : {
            ...prev,
            assignedUnits: (msg.payload ?? []).map((unit) => ({
              unitId: String(unit.unitId),
              unitName: unit.unitName ?? '',
              printsrvInstanceId: unit.printsrvInstanceId ?? null,
            })),
          }
    );
  }, []);

  const applyUnitTopologyChange = useCallback((payload: UnitPayload, action: ChangeAction) => {
    const unitDbId = String(payload.id);

    setProfile((prev) => {
      if (prev == null) return prev;
      const units = prev.assignedUnits;
      if (action === 'DELETE' || !payload.active) {
        if (!units.some((u) => u.unitId === unitDbId)) return prev;
        return { ...prev, assignedUnits: units.filter((u) => u.unitId !== unitDbId) };
      }
      if (payload.name == null) return prev;
      if (!units.some((u) => u.unitId === unitDbId && u.unitName !== payload.name)) return prev;
      return {
        ...prev,
        assignedUnits: units.map((u) =>
          u.unitId === unitDbId ? { ...u, unitName: payload.name ?? u.unitName } : u
        ),
      };
    });

    setSettings((prev) => {
      if (prev == null) return prev;
      if (action === 'DELETE' || !payload.active) {
        if (!prev.some((item) => item.unitId === unitDbId)) return prev;
        return prev.filter((item) => item.unitId !== unitDbId);
      }
      const existing = prev.find((item) => item.unitId === unitDbId);
      if (existing == null) {
        // Новый автомат появляется в настройках с серверными умолчаниями (оба включены),
        // список держим отсортированным по unitId — как отдаёт REST-снапшот.
        const added: NotificationSetting = {
          unitId: unitDbId,
          unitName: payload.name ?? '',
          techEnabled: true,
          masterEnabled: true,
        };
        return [...prev, added].sort((a, b) => Number(a.unitId) - Number(b.unitId));
      }
      if (payload.name == null || existing.unitName === payload.name) return prev;
      return prev.map((item) =>
        item.unitId === unitDbId ? { ...item, unitName: payload.name ?? item.unitName } : item
      );
    });
  }, []);

  const applyOwnSettingsChange = useCallback(
    (payload: UserNotificationSettingsPayload, action: ChangeAction) => {
      const unitDbId = payload.unitId != null ? String(payload.unitId) : null;
      if (unitDbId == null || settings == null) return;

      if (!settings.some((item) => item.unitId === unitDbId)) {
        // Автомата нет в текущем списке — список расходится с сервером,
        // безопаснее перечитать его целиком один раз.
        if (action !== 'DELETE') void refreshSettings();
        return;
      }

      setSettings((prev) => {
        if (prev == null) return prev;
        // Нет строки настроек (DELETE) → серверные умолчания «оба включены»;
        // неактивная строка → оба выключены (см. buildPreference на бэкенде).
        return prev.map((item) => {
          if (item.unitId !== unitDbId) return item;
          if (action === 'DELETE') return { ...item, techEnabled: true, masterEnabled: true };
          if (!payload.active) return { ...item, techEnabled: false, masterEnabled: false };
          return {
            ...item,
            techEnabled: payload.incidentNotificationsEnabled,
            masterEnabled: payload.androidCallNotificationsEnabled,
          };
        });
      });
    },
    [settings, refreshSettings]
  );

  const value = useMemo(
    () => ({
      profile,
      profileStatus,
      profileError,
      settings,
      settingsStatus,
      settingsError,
      refreshProfile,
      refreshSettings,
      applyLocalSetting,
      applyOwnEmployeeChange,
      applyAssignmentsFromWs,
      applyUnitTopologyChange,
      applyOwnSettingsChange,
    }),
    [
      profile,
      profileStatus,
      profileError,
      settings,
      settingsStatus,
      settingsError,
      refreshProfile,
      refreshSettings,
      applyLocalSetting,
      applyOwnEmployeeChange,
      applyAssignmentsFromWs,
      applyUnitTopologyChange,
      applyOwnSettingsChange,
    ]
  );

  return <UserProfileContext.Provider value={value}>{children}</UserProfileContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useUserProfile(): UserProfileContextValue {
  const ctx = useContext(UserProfileContext);
  if (!ctx) throw new Error('useUserProfile must be used within UserProfileProvider');
  return ctx;
}
