import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchUserProfile } from '../../api/profile';
import { logoutUser } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import { useAsyncFetch } from '../../hooks/useAsyncFetch';
import { getErrorBodyMessage } from '../../errors/AppError';
import type { UserProfile } from '../../types';
import { SkeletonBlock } from '../../components/skeleton/SkeletonBlock';

const ADMIN_PROFILE_COPY = Object.freeze({
  title: 'Профиль',
  roleLabel: 'Роль',
  workerCodeLabel: 'Табельный номер',
  monitoringButton: 'Мониторинг',
  logoutButtonAriaLabel: 'Выйти из аккаунта',
  logoutOverlayTitle: 'Выйти из аккаунта?',
  logoutOverlayMessage: 'Для повторного входа потребуется ввести код и пароль работника',
  logoutOverlayCancel: 'Отмена',
  logoutOverlayConfirm: 'Выйти',
});

function getInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '-';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
}

/**
 * Профиль администратора внутри админ-панели (/admin/profile).
 * В отличие от профиля мониторинга (/profile) рендерится под шапкой
 * администрирования и не содержит блоков «Закрепленное оборудование»
 * и «Настроить уведомления» — они относятся к сотрудникам мониторинга.
 */
export function AdminProfilePage() {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const profileFetch = useAsyncFetch<UserProfile>((signal) => fetchUserProfile(signal), [], {
    source: 'profile',
  });

  const [logoutOpen, setLogoutOpen] = useState(false);

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

  const profileInitials = useMemo(() => {
    if (!profileFetch.data?.fullName) return '—';
    return getInitials(profileFetch.data.fullName);
  }, [profileFetch.data?.fullName]);

  const profileError = profileFetch.error;
  const isProfileLoading = profileFetch.status === 'loading';

  return (
    <div className="p-3 lg:p-4">
      <div className="mb-3 lg:mb-4">
        <h1 className="text-xl font-bold text-[#1a1c1e]">{ADMIN_PROFILE_COPY.title}</h1>
      </div>

      <div className="mx-auto flex w-full max-w-[520px] flex-col gap-4">
        {isProfileLoading ? (
          <div className="flex w-full flex-col gap-4" aria-hidden="true">
            <div className="flex flex-col items-center gap-3">
              <SkeletonBlock height="64px" width="64px" borderRadius="14px" />
              <div className="text-center">
                <SkeletonBlock height="22px" width="220px" borderRadius="6px" />
                <div className="mt-2 inline-flex items-center gap-2">
                  <SkeletonBlock height="18px" width="120px" borderRadius="999px" />
                </div>
              </div>
            </div>
            <div className="rounded-[22px] border border-white/70 bg-white/90 px-4 py-4 shadow-[0_14px_40px_rgba(20,30,50,0.06)]">
              <SkeletonBlock height="12px" width="140px" borderRadius="6px" />
              <div className="mt-2">
                <SkeletonBlock height="18px" width="60%" borderRadius="6px" />
              </div>
            </div>
            <div>
              <SkeletonBlock height="44px" width="100%" borderRadius="12px" />
            </div>
          </div>
        ) : profileError ? (
          <p className="py-10 text-center text-sm text-[#74777F]">
            {getErrorBodyMessage(profileError)}
          </p>
        ) : (
          <>
            <div className="flex flex-col items-center gap-3">
              <div className="flex h-16 w-16 items-center justify-center rounded-[20px] bg-[#0b5da4] text-xl font-semibold text-white shadow-sm">
                {profileInitials}
              </div>
              <div className="text-center">
                <h2 className="text-lg font-semibold text-[#1A1C1E]">
                  {profileFetch.data?.fullName ?? '—'}
                </h2>
                <div className="mt-2 inline-flex items-center gap-2 rounded-full bg-[#eaf2ff] px-3 py-1 text-xs font-semibold text-[#1c4f8a]">
                  <span>{ADMIN_PROFILE_COPY.roleLabel}:</span>
                  <span>{profileFetch.data?.role ?? '—'}</span>
                </div>
              </div>
            </div>

            <div className="rounded-[22px] border border-white/70 bg-white/90 px-4 py-4 shadow-[0_14px_40px_rgba(20,30,50,0.06)]">
              <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-[#8a8f98]">
                {ADMIN_PROFILE_COPY.workerCodeLabel}
              </p>
              <p className="mt-2 text-base font-semibold text-[#1A1C1E]">
                {profileFetch.data?.workerCode ?? '—'}
              </p>
            </div>

            <div className="mt-2 flex items-center gap-3">
              <button
                type="button"
                onClick={() => navigate('/')}
                className="flex flex-1 items-center justify-center gap-2 rounded-[18px] bg-[#0b5da4] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_26px_rgba(11,93,164,0.32)]"
              >
                <span>{ADMIN_PROFILE_COPY.monitoringButton}</span>
              </button>
              <button
                type="button"
                onClick={handleLogoutClick}
                aria-label={ADMIN_PROFILE_COPY.logoutButtonAriaLabel}
                className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-[#EA4335] text-white shadow-[0_0_10px_rgba(234,67,53,0.18)] transition-all duration-200 ease-in-out active:scale-[0.98]"
              >
                <img
                  src="/assets/logout.svg"
                  alt=""
                  aria-hidden="true"
                  className="h-5 w-5 invert"
                />
              </button>
            </div>
          </>
        )}
      </div>

      {logoutOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-6 backdrop-blur-[2px]"
          onClick={handleLogoutCancel}
          role="presentation"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={ADMIN_PROFILE_COPY.logoutOverlayTitle}
            className="w-full max-w-[360px] rounded-[26px] bg-[#f8fafc] p-6 shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-base font-semibold text-[#1A1C1E]">
              {ADMIN_PROFILE_COPY.logoutOverlayTitle}
            </h3>
            <p className="mt-2 text-sm text-[#5F6368]">{ADMIN_PROFILE_COPY.logoutOverlayMessage}</p>
            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={handleLogoutCancel}
                className="rounded-2xl border border-[#e2e8f0] bg-white px-5 py-2.5 text-sm font-semibold text-[#1A1C1E] transition active:scale-[0.98]"
              >
                {ADMIN_PROFILE_COPY.logoutOverlayCancel}
              </button>
              <button
                type="button"
                onClick={handleLogoutConfirm}
                className="rounded-2xl bg-[#EA4335] px-5 py-2.5 text-sm font-semibold text-white shadow-[0_4px_14px_rgba(234,67,53,0.35)] transition active:scale-[0.98]"
              >
                {ADMIN_PROFILE_COPY.logoutOverlayConfirm}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
