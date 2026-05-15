import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { fetchUserProfile } from '../api/profile';
import { useAuth } from './AuthContext';

export type UnitAction = 'last-batch';

type AccessStatus = 'idle' | 'loading' | 'ready' | 'error';

type StoredAssignments = {
  userId: string;
  unitIds: string[];
  updatedAt: number;
};

interface AccessContextValue {
  assignedUnitIds: Set<string>;
  status: AccessStatus;
  refreshAssignments: (options?: { silent?: boolean }) => Promise<void>;
  isAssignedUnit: (unitId?: string | null) => boolean;
  canUseUnitAction: (action: UnitAction, unitId?: string | null) => boolean;
}

const AccessControlContext = createContext<AccessContextValue | null>(null);

const ACCESS_STORAGE_KEY = 'scada.assignedUnits';

const UNIT_ACTION_REQUIREMENTS: Record<UnitAction, 'assignment'> = {
  'last-batch': 'assignment',
};

function readStoredAssignments(userId: string): string[] | null {
  try {
    const raw = localStorage.getItem(ACCESS_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredAssignments | null;
    if (!parsed || parsed.userId !== userId) return null;
    if (!Array.isArray(parsed.unitIds)) return null;
    return parsed.unitIds.map((id) => String(id));
  } catch {
    return null;
  }
}

function writeStoredAssignments(userId: string, unitIds: string[]): void {
  try {
    const payload: StoredAssignments = {
      userId,
      unitIds,
      updatedAt: Date.now(),
    };
    localStorage.setItem(ACCESS_STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // ignore storage failures (private mode, quota, etc.)
  }
}

function clearStoredAssignments(): void {
  try {
    localStorage.removeItem(ACCESS_STORAGE_KEY);
  } catch {
    // ignore storage failures (private mode, quota, etc.)
  }
}

export function AccessControlProvider({ children }: { children: ReactNode }) {
  const { userId } = useAuth();
  const [assignedUnitIds, setAssignedUnitIds] = useState<Set<string>>(() => new Set());
  const [status, setStatus] = useState<AccessStatus>('idle');

  const refreshAssignments = useCallback(
    async (options?: { silent?: boolean }) => {
      if (!userId) return;
      const silent = options?.silent ?? false;
      if (!silent) setStatus('loading');

      try {
        const profile = await fetchUserProfile();
        const unitIds = profile.assignedUnits
          .map((unit) => unit.printsrvInstanceId ?? unit.unitId)
          .filter((unitId) => Boolean(unitId))
          .map((unitId) => String(unitId));
        const uniqueUnitIds = Array.from(new Set(unitIds));

        setAssignedUnitIds(new Set(uniqueUnitIds));
        setStatus('ready');
        writeStoredAssignments(userId, uniqueUnitIds);
      } catch {
        setStatus((prev) => (silent && prev === 'ready' ? prev : 'error'));
      }
    },
    [userId]
  );

  useEffect(() => {
    if (!userId) {
      setAssignedUnitIds(new Set());
      setStatus('idle');
      clearStoredAssignments();
      return;
    }

    const cachedUnitIds = readStoredAssignments(userId);
    const hasCache = cachedUnitIds != null;

    if (cachedUnitIds) {
      setAssignedUnitIds(new Set(cachedUnitIds));
      setStatus('ready');
    } else {
      setStatus('loading');
    }

    void refreshAssignments({ silent: hasCache });
  }, [userId, refreshAssignments]);

  const isAssignedUnit = useCallback(
    (unitId?: string | null) => {
      if (!unitId) return false;
      return assignedUnitIds.has(String(unitId));
    },
    [assignedUnitIds]
  );

  const canUseUnitAction = useCallback(
    (action: UnitAction, unitId?: string | null) => {
      if (!userId) return false;
      if (!unitId) return false;

      const requirement = UNIT_ACTION_REQUIREMENTS[action];
      if (requirement === 'assignment') {
        return isAssignedUnit(unitId);
      }
      return false;
    },
    [userId, isAssignedUnit]
  );

  const value = useMemo(
    () => ({
      assignedUnitIds,
      status,
      refreshAssignments,
      isAssignedUnit,
      canUseUnitAction,
    }),
    [assignedUnitIds, status, refreshAssignments, isAssignedUnit, canUseUnitAction]
  );

  return <AccessControlContext.Provider value={value}>{children}</AccessControlContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAccessControl(): AccessContextValue {
  const ctx = useContext(AccessControlContext);
  if (!ctx) throw new Error('useAccessControl must be used within AccessControlProvider');
  return ctx;
}
