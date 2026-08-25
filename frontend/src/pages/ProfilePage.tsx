import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { PAGE_FIXED_SECTION_STYLE } from '../config';
import { updateNotificationSetting } from '../api/profile';
import { logoutUser } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { useUserProfile } from '../context/UserProfileContext';
import { usePageHeader } from '../context/PageHeaderContext';
import { classifyError } from '../errors/classifyError';
import { getErrorBodyMessage } from '../errors/AppError';
import type { AppError } from '../errors/AppError';
import type { NotificationSetting } from '../types';
import { SkeletonBlock } from '../components/skeleton/SkeletonBlock';
import { UnitCardSkeleton } from '../components/skeleton/UnitCardSkeleton';

const PROFILE_COPY = Object.freeze({
  title: 'Профиль',
  roleLabel: 'Роль',
  workerCodeBadgeLabel: 'Таб. №',
  assignedUnitsLabel: 'Закрепленное оборудование',
  assignedUnitsEmpty: 'Нет закрепленного оборудования',
  notificationButton: 'Настроить уведомления',
  overlayTitle: 'Настройки уведомлений',
  overlayTechLabel: 'Тех. сбои',
  overlayTechHint: '(Автоматика)',
  overlayMasterLabel: 'Вызов',
  overlayMasterHint: '(Оператор)',
  notificationEmpty: 'Список автоматов пуст',
  updateErrorLabel: 'Не удалось сохранить изменения',
  logoutButtonAriaLabel: 'Выйти из аккаунта',
  logoutOverlayTitle: 'Выйти из аккаунта?',
  logoutOverlayMessage: 'Для повторного входа потребуется ввести код и пароль работника',
  logoutOverlayCancel: 'Отмена',
  logoutOverlayConfirm: 'Выйти',
});

/** CSS filter: перекраска иконок toggle в белый (включённое состояние, синий фон). */
const ICON_WHITE_FILTER =
  'brightness(0) saturate(100%) invert(100%) sepia(0%) saturate(0%) hue-rotate(0deg) brightness(100%) contrast(100%)';

/**
 * CSS filter: перекраска иконок toggle в фирменный синий (#0b5da4).
 * Выключенная кнопка белая — белая иконка на ней не читалась (см. issue #83).
 */
const ICON_BLUE_FILTER =
  'brightness(0) saturate(100%) invert(30%) sepia(96%) saturate(1180%) hue-rotate(195deg) brightness(95%) contrast(102%)';

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
  const location = useLocation();
  const { logout, isAdmin } = useAuth();

  // Откуда открыли профиль: из админ-панели показываем кнопку «Мониторинг»,
  // из мониторинга (или если источник неизвестен) — «Администрирование».
  const fromPath = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
  const fromAdmin = fromPath?.startsWith('/admin') ?? false;

  usePageHeader(PROFILE_COPY.title, undefined, 'default');

  // Профиль и настройки живут в UserProfileContext: первоначальная загрузка — REST,
  // все дальнейшие изменения приходят по /ws/live (см. RootLayout).
  const {
    profile,
    profileStatus,
    profileError,
    settings,
    settingsStatus,
    settingsError,
    applyLocalSetting,
  } = useUserProfile();

  const [settingsOpen, setSettingsOpen] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [pendingUnits, setPendingUnits] = useState<string[]>([]);
  const [updateError, setUpdateError] = useState<AppError | null>(null);

  const handleLogoutClick = useCallback(() => {
    setLogoutOpen(true);
  }, []);

  const handleLogoutCancel = useCallback(() => {
    setLogoutOpen(false);
  }, []);

  const handleLogoutConfirm = useCallback(async () => {
    setLogoutOpen(false);
    await logoutUser();
    logout();
    navigate('/login', { replace: true });
    // Принудительно перезагружаем страницу для полной очистки состояния
    window.location.reload();
  }, [logout, navigate]);

  useEffect(() => {
    if (!settingsOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [settingsOpen]);

  const profileInitials = useMemo(() => {
    if (!profile?.fullName) return '—';
    return getInitials(profile.fullName);
  }, [profile?.fullName]);

  const assignedUnitIds = useMemo(() => {
    const units = profile?.assignedUnits ?? [];
    return new Set(units.map((unit) => unit.unitId));
  }, [profile?.assignedUnits]);

  const handleToggle = useCallback(
    async (unitId: string, field: 'techEnabled' | 'masterEnabled') => {
      const current = settings?.find((item) => item.unitId === unitId);
      if (!current) return;

      const updated: NotificationSetting = {
        ...current,
        techEnabled: field === 'techEnabled' ? !current.techEnabled : current.techEnabled,
        masterEnabled: field === 'masterEnabled' ? !current.masterEnabled : current.masterEnabled,
      };

      applyLocalSetting(updated);
      setPendingUnits((prev) => mergePending(prev, unitId, true));
      setUpdateError(null);

      try {
        await updateNotificationSetting({
          unitId,
          techEnabled: updated.techEnabled,
          masterEnabled: updated.masterEnabled,
        });
      } catch (error) {
        applyLocalSetting(current);
        setUpdateError(classifyError(error, 'notification-settings'));
      } finally {
        setPendingUnits((prev) => mergePending(prev, unitId, false));
      }
    },
    [settings, applyLocalSetting]
  );

  const isProfileLoading = profileStatus === 'loading' || profileStatus === 'idle';

  function ProfileSkeleton() {
    return (
      <div className="mx-auto flex h-full w-full max-w-[520px] flex-col gap-3" aria-hidden="true">
        <div className="flex flex-col items-center gap-2">
          <SkeletonBlock height="56px" width="56px" borderRadius="14px" />
          <div className="text-center">
            <SkeletonBlock height="22px" width="220px" borderRadius="6px" />
            <div className="mt-2 inline-flex items-center gap-2">
              <SkeletonBlock height="18px" width="90px" borderRadius="999px" />
              <SkeletonBlock height="18px" width="90px" borderRadius="999px" />
            </div>
          </div>
        </div>

        <div className="rounded-[22px] border border-white/70 bg-white/90 px-4 py-3 shadow-[0_14px_40px_rgba(20,30,50,0.06)]">
          <SkeletonBlock height="12px" width="180px" borderRadius="6px" />
          <div className="mt-3">
            <SkeletonBlock height="16px" width="40%" borderRadius="6px" />
          </div>
        </div>

        <div className="mt-auto">
          <SkeletonBlock height="44px" width="100%" borderRadius="12px" />
        </div>
      </div>
    );
  }

  /*
   * Экран профиля зафиксирован целиком (без прокрутки страницы): шапка с
   * иконкой/ФИО/бейджами и нижние кнопки всегда на экране терминала 4.2".
   * Прокручивается только внутренний фрейм списка закреплённого оборудования;
   * надпись «Закрепленное оборудование» вынесена из прокручиваемой области
   * и работает как sticky-заголовок карточки.
   */
  return (
    <section style={PAGE_FIXED_SECTION_STYLE}>
      <main className="flex min-h-0 flex-1 flex-col px-4 pb-4 pt-4 sm:px-6">
        {isProfileLoading ? (
          <ProfileSkeleton />
        ) : profileError ? (
          <p className="py-10 text-center text-sm text-[#74777F]">
            {getErrorBodyMessage(profileError)}
          </p>
        ) : (
          <div className="mx-auto flex h-full w-full max-w-[520px] min-h-0 flex-col gap-3">
            <div className="flex flex-shrink-0 flex-col items-center gap-2">
              <div className="flex h-14 w-14 items-center justify-center rounded-[18px] bg-[#0b5da4] text-xl font-semibold text-white shadow-sm">
                {profileInitials}
              </div>
              <div className="text-center">
                <h2 className="text-lg font-semibold text-[#1A1C1E]">{profile?.fullName ?? '—'}</h2>
                <div className="mt-1.5 flex flex-wrap items-center justify-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-[#eaf2ff] px-3 py-1 text-xs font-semibold text-[#1c4f8a]">
                    <span>{PROFILE_COPY.roleLabel}:</span>
                    <span>{profile?.role ?? '—'}</span>
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-[#eaf2ff] px-3 py-1 text-xs font-semibold text-[#1c4f8a]">
                    <span>{PROFILE_COPY.workerCodeBadgeLabel}:</span>
                    <span>{profile?.workerCode ?? '—'}</span>
                  </span>
                </div>
              </div>
            </div>

            {!isAdmin && (
              <div className="flex min-h-0 flex-1 flex-col rounded-[22px] border border-white/70 bg-white/90 px-4 py-3 shadow-[0_14px_40px_rgba(20,30,50,0.06)]">
                <p className="flex-shrink-0 text-[10px] font-bold uppercase tracking-[0.2em] text-[#8a8f98]">
                  {PROFILE_COPY.assignedUnitsLabel}
                </p>
                <div data-scroll className="mt-2 min-h-0 flex-1 overflow-y-auto">
                  {profile?.assignedUnits?.length ? (
                    <ul className="space-y-2 pr-1 text-sm font-semibold text-[#1A1C1E]">
                      {profile.assignedUnits.map((unit) => (
                        <li key={unit.unitId} className="flex items-start gap-2">
                          <span className="text-[#1c6fe8]">•</span>
                          <span>{unit.unitName}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm font-medium text-[#74777F]">
                      {PROFILE_COPY.assignedUnitsEmpty}
                    </p>
                  )}
                </div>
              </div>
            )}

            <div className="mt-auto flex flex-shrink-0 items-center gap-3 pt-1">
              <button
                type="button"
                onClick={handleLogoutClick}
                aria-label={PROFILE_COPY.logoutButtonAriaLabel}
                className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-[#EA4335] text-white shadow-[0_0_10px_rgba(234,67,53,0.18)] transition-all duration-200 ease-in-out active:scale-[0.98]"
              >
                <img
                  src="/assets/logout.svg"
                  alt=""
                  aria-hidden="true"
                  className="h-5 w-5 invert"
                />
              </button>
              {isAdmin ? (
                fromAdmin ? (
                  <button
                    type="button"
                    onClick={() => navigate('/')}
                    className="flex flex-1 items-center justify-center gap-2 rounded-[18px] bg-[#0b5da4] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_26px_rgba(11,93,164,0.32)]"
                  >
                    <span>Мониторинг</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => navigate('/admin')}
                    className="flex flex-1 items-center justify-center gap-2 rounded-[18px] bg-[#111827] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_26px_rgba(15,23,42,0.32)]"
                  >
                    <span>Администрирование</span>
                  </button>
                )
              ) : (
                <button
                  type="button"
                  onClick={() => setSettingsOpen(true)}
                  className="flex flex-1 items-center justify-center gap-2 rounded-[18px] bg-[#111827] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_26px_rgba(15,23,42,0.32)]"
                >
                  <span>{PROFILE_COPY.notificationButton}</span>
                </button>
              )}
            </div>
          </div>
        )}
      </main>

      {settingsOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4 py-4 backdrop-blur-[2px]">
          <div
            role="dialog"
            aria-modal="true"
            aria-label={PROFILE_COPY.overlayTitle}
            className="flex h-full max-h-[85vh] w-full max-w-[520px] flex-col overflow-hidden rounded-[26px] bg-[#f8fafc] shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
          >
            <div className="flex items-center justify-between border-b border-white/70 px-4 py-2.5">
              <h3 className="text-sm font-semibold text-[#1A1C1E]">{PROFILE_COPY.overlayTitle}</h3>
              <button
                type="button"
                onClick={() => setSettingsOpen(false)}
                className="flex h-8 w-8 items-center justify-center rounded-full border border-[#e2e8f0] bg-white text-[#5f6368]"
                aria-label="Закрыть"
              >
                ✕
              </button>
            </div>

            {/* Легенда типов — строго одна строка: на терминале CSS-viewport уже
                физических 480px, поэтому пилюли с подложкой не помещались и
                переносились. Иконки синие — в цвет включённого toggle. */}
            <div className="flex items-center justify-evenly gap-2 overflow-hidden whitespace-nowrap border-b border-white/70 px-4 py-2 text-[11px] font-semibold text-[#374151]">
              <span className="inline-flex items-center gap-1.5">
                <img
                  src="/assets/lightning.svg"
                  alt=""
                  aria-hidden="true"
                  className="h-4 w-4"
                  style={{ filter: ICON_BLUE_FILTER }}
                />
                <span>{PROFILE_COPY.overlayTechLabel}</span>
                <span className="font-medium text-[#7b8190]">{PROFILE_COPY.overlayTechHint}</span>
              </span>
              <span className="inline-flex items-center gap-1.5">
                <img
                  src="/assets/message.svg"
                  alt=""
                  aria-hidden="true"
                  className="h-4 w-4"
                  style={{ filter: ICON_BLUE_FILTER }}
                />
                <span>{PROFILE_COPY.overlayMasterLabel}</span>
                <span className="font-medium text-[#7b8190]">{PROFILE_COPY.overlayMasterHint}</span>
              </span>
            </div>

            <div data-scroll className="flex-1 overflow-y-auto px-4 pb-4">
              {settingsStatus === 'loading' || settingsStatus === 'idle' ? (
                <div className="space-y-2.5">
                  <UnitCardSkeleton />
                  <UnitCardSkeleton />
                  <UnitCardSkeleton />
                </div>
              ) : settingsError ? (
                <p className="py-6 text-center text-sm text-[#7b8190]">
                  {getErrorBodyMessage(settingsError)}
                </p>
              ) : (settings ?? []).length === 0 ? (
                <p className="py-6 text-center text-sm text-[#7b8190]">
                  {PROFILE_COPY.notificationEmpty}
                </p>
              ) : (
                <div className="space-y-2.5">
                  {updateError && (
                    <div className="rounded-[18px] border border-[#f1d4d6] bg-[#fff8f8] px-4 py-3 text-xs font-semibold text-[#9f3138]">
                      {PROFILE_COPY.updateErrorLabel}: {updateError.message}
                    </div>
                  )}
                  {(settings ?? []).map((item) => {
                    const isPending = pendingUnits.includes(item.unitId);
                    const techActive = item.techEnabled;
                    const masterActive = item.masterEnabled;
                    const isAssigned = assignedUnitIds.has(item.unitId);

                    const buttonBase =
                      'flex h-10 w-10 items-center justify-center rounded-full border text-base font-semibold transition';

                    const techClass = techActive
                      ? 'border-[#0b5da4] bg-[#0b5da4]'
                      : 'border-[#dce2ea] bg-white';

                    const masterClass = masterActive
                      ? 'border-[#0b5da4] bg-[#0b5da4]'
                      : 'border-[#dce2ea] bg-white';

                    return (
                      <div
                        key={item.unitId}
                        className="flex items-center justify-between gap-3 rounded-[20px] border border-white/70 bg-white px-3.5 py-2.5 shadow-sm"
                      >
                        <div className="text-sm font-semibold text-[#1A1C1E]">
                          <span>{item.unitName}</span>
                          {isAssigned ? (
                            <span className="ml-2 text-sm font-semibold text-[#1A1C1E]">
                              • Ваш автомат
                            </span>
                          ) : null}
                        </div>
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
                            <img
                              src="/assets/lightning.svg"
                              alt=""
                              aria-hidden="true"
                              className="h-5 w-5"
                              style={{ filter: techActive ? ICON_WHITE_FILTER : ICON_BLUE_FILTER }}
                            />
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
                            <img
                              src="/assets/message.svg"
                              alt=""
                              aria-hidden="true"
                              className="h-5 w-5"
                              style={{
                                filter: masterActive ? ICON_WHITE_FILTER : ICON_BLUE_FILTER,
                              }}
                            />
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

      {logoutOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-6 backdrop-blur-[2px]"
          onClick={handleLogoutCancel}
          role="presentation"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={PROFILE_COPY.logoutOverlayTitle}
            className="w-full max-w-[360px] rounded-[26px] bg-[#f8fafc] p-6 shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-base font-semibold text-[#1A1C1E]">
              {PROFILE_COPY.logoutOverlayTitle}
            </h3>
            <p className="mt-2 text-sm text-[#5F6368]">{PROFILE_COPY.logoutOverlayMessage}</p>
            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={handleLogoutCancel}
                className="rounded-2xl border border-[#e2e8f0] bg-white px-5 py-2.5 text-sm font-semibold text-[#1A1C1E] transition active:scale-[0.98]"
              >
                {PROFILE_COPY.logoutOverlayCancel}
              </button>
              <button
                type="button"
                onClick={handleLogoutConfirm}
                className="rounded-2xl bg-[#EA4335] px-5 py-2.5 text-sm font-semibold text-white shadow-[0_4px_14px_rgba(234,67,53,0.35)] transition active:scale-[0.98]"
              >
                {PROFILE_COPY.logoutOverlayConfirm}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
