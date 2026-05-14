import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PAGE_FADE_SECTION_STYLE, UI_COPY } from '../config';
import {
  fetchNotificationSettings,
  fetchUserProfile,
  updateNotificationSetting,
} from '../api/profile';
import { usePageHeader } from '../context/PageHeaderContext';
import { useAsyncFetch } from '../hooks/useAsyncFetch';
import { classifyError } from '../errors/classifyError';
import { getErrorBodyMessage } from '../errors/AppError';
import type { AppError } from '../errors/AppError';
import type { NotificationSetting, UserProfile } from '../types';

const PROFILE_COPY = Object.freeze({
  title: 'Профиль',
  roleLabel: 'Роль',
  workerCodeLabel: 'ID сотрудника',
  assignedUnitsLabel: 'Закрепленное оборудование',
  assignedUnitsEmpty: 'Нет закрепленного оборудования',
  notificationButton: 'Настроить уведомления',
  overlayTitle: 'Настройки уведомлений',
  overlayTechLabel: 'Тех. сбои',
  overlayTechHint: '(Автоматика)',
  overlayMasterLabel: 'Вызов',
  overlayMasterHint: '(Оператор)',
  notificationEmpty: 'Список аппаратов пуст',
  updateErrorLabel: 'Не удалось сохранить изменения',
});

function getInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '-';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
}

function mergePending(prev: string[], unitId: string, pending: boolean): string[] {
  if (pending) return prev.includes(unitId) ? prev : [...prev, unitId];
  return prev.filter((id) => id !== unitId);
}

export function ProfilePage() {
  const navigate = useNavigate();
  const handleBack = useCallback(() => navigate(-1), [navigate]);

  usePageHeader(PROFILE_COPY.title, undefined, 'default', handleBack);

  const profileFetch = useAsyncFetch<UserProfile>((signal) => fetchUserProfile(signal), [], {
    source: 'profile',
  });

  const settingsFetch = useAsyncFetch<NotificationSetting[]>(
    (signal) => fetchNotificationSettings(signal),
    [],
    { source: 'notification-settings' }
  );

  const [settings, setSettings] = useState<NotificationSetting[]>([]);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [pendingUnits, setPendingUnits] = useState<string[]>([]);
  const [updateError, setUpdateError] = useState<AppError | null>(null);

  useEffect(() => {
    if (!settingsFetch.data) return;
    setSettings(settingsFetch.data);
  }, [settingsFetch.data]);

  useEffect(() => {
    if (!settingsOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [settingsOpen]);

  const profileInitials = useMemo(() => {
    if (!profileFetch.data?.fullName) return '—';
    return getInitials(profileFetch.data.fullName);
  }, [profileFetch.data?.fullName]);

  const handleToggle = useCallback(
    async (unitId: string, field: 'techEnabled' | 'masterEnabled') => {
      const current = settings.find((item) => item.unitId === unitId);
      if (!current) return;

      const updated: NotificationSetting = {
        ...current,
        techEnabled: field === 'techEnabled' ? !current.techEnabled : current.techEnabled,
        masterEnabled: field === 'masterEnabled' ? !current.masterEnabled : current.masterEnabled,
      };

      setSettings((prev) => prev.map((item) => (item.unitId === unitId ? updated : item)));
      setPendingUnits((prev) => mergePending(prev, unitId, true));
      setUpdateError(null);

      try {
        await updateNotificationSetting({
          unitId,
          techEnabled: updated.techEnabled,
          masterEnabled: updated.masterEnabled,
        });
      } catch (error) {
        setSettings((prev) => prev.map((item) => (item.unitId === unitId ? current : item)));
        setUpdateError(classifyError(error, 'notification-settings'));
      } finally {
        setPendingUnits((prev) => mergePending(prev, unitId, false));
      }
    },
    [settings]
  );

  const profileError = profileFetch.error;
  const isProfileLoading = profileFetch.status === 'loading';

  return (
    <section data-scroll style={PAGE_FADE_SECTION_STYLE}>
      <main className="px-5 pb-12 pt-6 sm:px-7">
        <div className="mx-auto flex w-full max-w-[520px] flex-col gap-4">
          {profileError ? (
            <div className="rounded-[26px] border border-[#f1d4d6] bg-[#fff8f8] px-4 py-5 text-center">
              <p className="text-sm font-semibold text-[#9f3138]">
                {getErrorBodyMessage(profileError)}
              </p>
              <button
                type="button"
                onClick={profileFetch.refetch}
                className="mt-3 rounded-full bg-[#0b5da4] px-5 py-2 text-xs font-semibold text-white"
              >
                {UI_COPY.retryAction}
              </button>
            </div>
          ) : (
            <>
              <div className="flex flex-col items-center gap-3">
                <div className="flex h-16 w-16 items-center justify-center rounded-[20px] bg-[#0b5da4] text-xl font-semibold text-white shadow-sm">
                  {isProfileLoading ? '…' : profileInitials}
                </div>
                <div className="text-center">
                  <h2 className="text-lg font-semibold text-[#1A1C1E]">
                    {isProfileLoading
                      ? 'Загрузка профиля...'
                      : (profileFetch.data?.fullName ?? '—')}
                  </h2>
                  <div className="mt-2 inline-flex items-center gap-2 rounded-full bg-[#eaf2ff] px-3 py-1 text-xs font-semibold text-[#1c4f8a]">
                    <span>{PROFILE_COPY.roleLabel}:</span>
                    <span>{profileFetch.data?.role ?? '—'}</span>
                  </div>
                </div>
              </div>

              <div className="rounded-[22px] border border-white/70 bg-white/90 px-4 py-4 shadow-[0_14px_40px_rgba(20,30,50,0.06)]">
                <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-[#8a8f98]">
                  {PROFILE_COPY.workerCodeLabel}
                </p>
                <p className="mt-2 text-base font-semibold text-[#1A1C1E]">
                  {profileFetch.data?.workerCode ?? '—'}
                </p>
              </div>

              <div className="rounded-[22px] border border-white/70 bg-white/90 px-4 py-4 shadow-[0_14px_40px_rgba(20,30,50,0.06)]">
                <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-[#8a8f98]">
                  {PROFILE_COPY.assignedUnitsLabel}
                </p>
                {profileFetch.data?.assignedUnits?.length ? (
                  <ul className="mt-3 space-y-2 text-sm font-semibold text-[#1A1C1E]">
                    {profileFetch.data.assignedUnits.map((unit) => (
                      <li key={unit.unitId} className="flex items-start gap-2">
                        <span className="text-[#1c6fe8]">•</span>
                        <span>{unit.unitName}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-3 text-sm font-medium text-[#74777F]">
                    {PROFILE_COPY.assignedUnitsEmpty}
                  </p>
                )}
              </div>

              <button
                type="button"
                onClick={() => setSettingsOpen(true)}
                className="mt-2 flex w-full items-center justify-center gap-2 rounded-[18px] bg-[#111827] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_26px_rgba(15,23,42,0.32)]"
              >
                <span aria-hidden="true">🔔</span>
                <span>{PROFILE_COPY.notificationButton}</span>
              </button>
            </>
          )}
        </div>
      </main>

      {settingsOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4 py-6 backdrop-blur-[2px]">
          <div
            role="dialog"
            aria-modal="true"
            aria-label={PROFILE_COPY.overlayTitle}
            className="flex h-full max-h-[80vh] w-full max-w-[520px] flex-col overflow-hidden rounded-[26px] bg-[#f8fafc] shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
          >
            <div className="flex items-center justify-between border-b border-white/70 px-5 py-4">
              <h3 className="text-base font-semibold text-[#1A1C1E]">
                {PROFILE_COPY.overlayTitle}
              </h3>
              <button
                type="button"
                onClick={() => setSettingsOpen(false)}
                className="flex h-9 w-9 items-center justify-center rounded-full border border-[#e2e8f0] bg-white text-[#5f6368]"
                aria-label="Закрыть"
              >
                ✕
              </button>
            </div>

            <div className="px-5 py-4">
              <div className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-2 text-[11px] font-semibold text-[#374151] shadow-sm">
                <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-[#0b5da4] text-white">
                  ⚡
                </span>
                <span>{PROFILE_COPY.overlayTechLabel}</span>
                <span className="text-[#7b8190]">{PROFILE_COPY.overlayTechHint}</span>
              </div>
              <div className="mt-3 inline-flex items-center gap-2 rounded-full bg-white px-3 py-2 text-[11px] font-semibold text-[#374151] shadow-sm">
                <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-[#0b5da4] text-white">
                  💬
                </span>
                <span>{PROFILE_COPY.overlayMasterLabel}</span>
                <span className="text-[#7b8190]">{PROFILE_COPY.overlayMasterHint}</span>
              </div>
            </div>

            <div data-scroll className="flex-1 overflow-y-auto px-5 pb-5">
              {settingsFetch.error ? (
                <div className="rounded-[20px] border border-[#f1d4d6] bg-[#fff8f8] px-4 py-4 text-center">
                  <p className="text-sm font-semibold text-[#9f3138]">
                    {getErrorBodyMessage(settingsFetch.error)}
                  </p>
                  <button
                    type="button"
                    onClick={settingsFetch.refetch}
                    className="mt-3 rounded-full bg-[#0b5da4] px-5 py-2 text-xs font-semibold text-white"
                  >
                    {UI_COPY.retryAction}
                  </button>
                </div>
              ) : settingsFetch.status === 'loading' && settings.length === 0 ? (
                <p className="py-6 text-center text-sm text-[#7b8190]">Загрузка...</p>
              ) : settings.length === 0 ? (
                <p className="py-6 text-center text-sm text-[#7b8190]">
                  {PROFILE_COPY.notificationEmpty}
                </p>
              ) : (
                <div className="space-y-3">
                  {updateError && (
                    <div className="rounded-[18px] border border-[#f1d4d6] bg-[#fff8f8] px-4 py-3 text-xs font-semibold text-[#9f3138]">
                      {PROFILE_COPY.updateErrorLabel}: {updateError.message}
                    </div>
                  )}
                  {settings.map((item) => {
                    const isPending = pendingUnits.includes(item.unitId);
                    const techActive = item.techEnabled;
                    const masterActive = item.masterEnabled;

                    const buttonBase =
                      'flex h-11 w-11 items-center justify-center rounded-2xl border text-base font-semibold transition';

                    const techClass = techActive
                      ? 'border-[#0b5da4] bg-[#0b5da4] text-white'
                      : 'border-[#dce2ea] bg-white text-[#7b8190]';

                    const masterClass = masterActive
                      ? 'border-[#0b5da4] bg-[#0b5da4] text-white'
                      : 'border-[#dce2ea] bg-white text-[#7b8190]';

                    return (
                      <div
                        key={item.unitId}
                        className="flex items-center justify-between gap-3 rounded-[20px] border border-white/70 bg-white px-4 py-3 shadow-sm"
                      >
                        <div className="text-sm font-semibold text-[#1A1C1E]">{item.unitName}</div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            aria-pressed={techActive}
                            disabled={isPending}
                            onClick={() => handleToggle(item.unitId, 'techEnabled')}
                            className={`${buttonBase} ${techClass} ${
                              isPending ? 'opacity-60' : ''
                            }`}
                          >
                            ⚡
                          </button>
                          <button
                            type="button"
                            aria-pressed={masterActive}
                            disabled={isPending}
                            onClick={() => handleToggle(item.unitId, 'masterEnabled')}
                            className={`${buttonBase} ${masterClass} ${
                              isPending ? 'opacity-60' : ''
                            }`}
                          >
                            💬
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
