import { useCallback, useEffect, useRef, useState } from 'react';
import {
  getUnitErrorGroups,
  getUnitStatusLevel,
  UNIT_STATUS_CLASS,
} from '../constants/statusUtils';
import { UI_PALETTE } from '../config';
import { UnitErrorBoard } from './UnitErrorBoard';
import { useAccessControl } from '../context/AccessControlContext';
import { ConfirmationOverlay } from './ConfirmationOverlay';
import { useLastBatch } from '../hooks/useLastBatch';
import { useAuth } from '../context/AuthContext';
import type { AlertData, NotificationData, Unit } from '../types';

/**
 * Карточка аппарата на экране цеха.
 *
 * Единый источник правды по статусу и группировке ошибок:
 * {@link ../constants/statusUtils.ts}. Компонент только отображает результат,
 * не повторяет доменные правила.
 *
 * Слой уведомлений: если для аппарата есть активное notification —
 * на карточке показывается индикатор-колокольчик (жёлтый).
 *
 * Свайп вправо (только для закреплённых автоматов), поведение в духе iOS:
 * - карточка движется с сопротивлением ({@link SWIPE_RESISTANCE}) — свайп
 *   требует осознанного движения, случайные касания не срабатывают;
 * - слева под карточкой растёт синяя «пилюля» с иконкой колокольчика
 *   и подписью действия;
 * - как только карточка проходит «точку невозврата»
 *   ({@link SWIPE_COMMIT_THRESHOLD_PX}), при отпускании overlay подтверждения
 *   открывается сразу, без тапа по раскрытой области;
 * - неполный свайп оставляет «пилюлю» раскрытой — тап по ней также
 *   вызывает overlay; тап по сдвинутой карточке возвращает её на место.
 */

interface Props {
  unit: Unit;
  alerts: Map<string, AlertData>;
  /** Активные производственные уведомления (из AppContext). */
  notifications?: Map<string, NotificationData>;
  onClick: () => void;
}

/** «Точка невозврата»: отпускание за этим смещением сразу открывает overlay. */
const SWIPE_COMMIT_THRESHOLD_PX = 120;
/** Ширина раскрытой «пилюли» действия после неполного свайпа. */
const SWIPE_REVEAL_PX = 108;
/** Сопротивление движению карточки (< 1) — визуально «утяжеляет» свайп. */
const SWIPE_RESISTANCE = 0.7;
/** Длительность анимации «улёта» карточки перед открытием overlay. */
const COMMIT_ANIMATION_MS = 180;

/** CSS filter для перекраски bell.svg в белый цвет. */
const BELL_WHITE_FILTER =
  'brightness(0) saturate(100%) invert(100%) sepia(0%) saturate(0%) hue-rotate(0deg) brightness(100%) contrast(100%)';

/** CSS filter для перекраски bell.svg в цвет toggle (#F59E0B). */
const BELL_AMBER_FILTER =
  'brightness(0) saturate(100%) invert(59%) sepia(97%) saturate(1214%) hue-rotate(359deg) brightness(101%) contrast(96%)';

export function UnitCard({ unit, alerts, notifications, onClick }: Props) {
  const { userId } = useAuth();
  const { isAssignedUnit } = useAccessControl();
  const statusLevel = getUnitStatusLevel(unit, alerts);
  const isPending = statusLevel === 'pending';
  const isOffline = statusLevel === 'offline';
  const isCritical = statusLevel === 'critical';
  const isAssigned = isAssignedUnit(unit.id);

  // Notification — отдельный визуальный слой (не заменяет статус).
  const notification = notifications?.get(String(unit.id));
  const isActiveByMe = notification != null && userId != null && notification.creatorId === userId;

  const statusClass = UNIT_STATUS_CLASS[statusLevel];

  const errorGroups = isCritical ? getUnitErrorGroups(unit.id, alerts) : [];

  // ── Swipe state ────────────────────────────────────────────────────────
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [swipeOffset, setSwipeOffset] = useState(0);
  const [completing, setCompleting] = useState(false);
  const touchStartX = useRef<number | null>(null);
  const startOffset = useRef(0);
  const containerWidth = useRef(0);
  const isTouching = useRef(false);
  const commitTimer = useRef<number | null>(null);

  // ── Confirmation overlay ───────────────────────────────────────────────
  const [overlayOpen, setOverlayOpen] = useState(false);
  const { sendLastBatch, reset: resetLastBatch } = useLastBatch();

  useEffect(
    () => () => {
      if (commitTimer.current != null) window.clearTimeout(commitTimer.current);
    },
    []
  );

  const handleTouchStart = useCallback(
    (e: React.TouchEvent) => {
      if (!isAssigned || completing) return;
      touchStartX.current = e.touches[0].clientX;
      startOffset.current = swipeOffset;
      containerWidth.current = containerRef.current?.offsetWidth ?? 0;
      isTouching.current = true;
    },
    [isAssigned, completing, swipeOffset]
  );

  const handleTouchMove = useCallback(
    (e: React.TouchEvent) => {
      if (!isAssigned || touchStartX.current == null) return;
      const delta = e.touches[0].clientX - touchStartX.current;
      const raw = startOffset.current + delta;
      if (raw <= 0) {
        setSwipeOffset(0);
        return;
      }
      // Свайп вправо: сопротивление делает карточку «тяжелее»
      const max = containerWidth.current > 0 ? containerWidth.current : Number.POSITIVE_INFINITY;
      setSwipeOffset(Math.min(max, raw * SWIPE_RESISTANCE));
    },
    [isAssigned]
  );

  /** Карточка «улетает» вправо, затем overlay открывается без дополнительного тапа. */
  const commitSwipe = useCallback(() => {
    const width = containerWidth.current > 0 ? containerWidth.current : SWIPE_REVEAL_PX;
    setCompleting(true);
    setSwipeOffset(width);
    commitTimer.current = window.setTimeout(() => {
      commitTimer.current = null;
      setCompleting(false);
      setOverlayOpen(true);
    }, COMMIT_ANIMATION_MS);
  }, []);

  const handleTouchEnd = useCallback(() => {
    touchStartX.current = null;
    isTouching.current = false;
    if (swipeOffset >= SWIPE_COMMIT_THRESHOLD_PX) {
      commitSwipe();
    } else if (swipeOffset > SWIPE_REVEAL_PX / 2) {
      setSwipeOffset(SWIPE_REVEAL_PX);
    } else {
      setSwipeOffset(0);
    }
  }, [swipeOffset, commitSwipe]);

  const handleTouchCancel = useCallback(() => {
    touchStartX.current = null;
    isTouching.current = false;
    setSwipeOffset(0);
  }, []);

  const handleRevealClick = useCallback(() => {
    if (completing) return;
    if (swipeOffset > 0) setOverlayOpen(true);
  }, [completing, swipeOffset]);

  const handleCardClick = useCallback(() => {
    if (completing) return;
    // Тап по сдвинутой карточке просто возвращает её на место
    if (swipeOffset > 0) {
      setSwipeOffset(0);
      return;
    }
    onClick();
  }, [completing, swipeOffset, onClick]);

  const handleConfirm = useCallback(async () => {
    setOverlayOpen(false);
    await sendLastBatch(String(unit.id));
    setSwipeOffset(0);
    setTimeout(() => {
      resetLastBatch();
    }, 2000);
  }, [unit.id, sendLastBatch, resetLastBatch]);

  const handleCancel = useCallback(() => {
    setOverlayOpen(false);
    setSwipeOffset(0);
  }, []);

  // offline: карточка некликабельна; card-static отключает cursor:pointer и :active-scale.
  const interactiveProps = isOffline
    ? { 'aria-disabled': true as const }
    : { onClick: handleCardClick, role: 'button' as const };

  // Прогресс раскрытия «пилюли» (0..1) и прогресс за «точкой невозврата» (0..1)
  const revealProgress = Math.min(1, swipeOffset / SWIPE_REVEAL_PX);
  const fullWidth = containerWidth.current;
  const overProgress =
    fullWidth > 0
      ? Math.min(
          1,
          Math.max(
            0,
            (swipeOffset - SWIPE_COMMIT_THRESHOLD_PX) /
              Math.max(1, fullWidth - SWIPE_COMMIT_THRESHOLD_PX)
          )
        )
      : 0;
  const pillWidth = SWIPE_REVEAL_PX + overProgress * Math.max(0, fullWidth - SWIPE_REVEAL_PX - 24);

  return (
    <>
      <div
        ref={containerRef}
        className="relative select-none overflow-hidden rounded-[24px]"
        style={{ touchAction: 'pan-y' }}
      >
        {/* Фоновый слой: «пилюля» действия в духе iOS — растёт за карточкой */}
        {isAssigned && (
          <div
            className="absolute inset-0 flex flex-col justify-center px-3"
            style={{ zIndex: 0 }}
            onClick={handleRevealClick}
            role="button"
            aria-label={isActiveByMe ? 'Снять уведомление' : 'Последняя партия'}
          >
            <div
              className="flex items-center justify-center"
              style={{
                height: '56px',
                width: `${pillWidth}px`,
                borderRadius: '18px',
                backgroundColor: '#3B82F6',
                opacity: revealProgress,
                boxShadow: swipeOffset > 0 ? '0 10px 24px rgba(59, 130, 246, 0.35)' : undefined,
                transition: isTouching.current ? undefined : 'width 0.25s ease, opacity 0.25s ease',
              }}
            >
              <img
                src={isActiveByMe ? '/assets/bell-off.svg' : '/assets/bell.svg'}
                alt=""
                aria-hidden="true"
                className="h-7 w-7"
                style={{ filter: BELL_WHITE_FILTER }}
              />
            </div>
            <div
              className="mt-1 text-center"
              style={{
                width: `${pillWidth}px`,
                opacity: revealProgress * (1 - overProgress),
                transition: isTouching.current ? undefined : 'width 0.25s ease, opacity 0.25s ease',
              }}
            >
              <span className="text-[11px] font-medium leading-none text-gray-500">
                {isActiveByMe ? 'Снять' : 'Уведомить'}
              </span>
            </div>
          </div>
        )}

        {/* Карточка (сдвигается вправо при свайпе) */}
        <div
          className={`card p-4 md:h-full ${statusClass}${isOffline ? ' card-static' : ''}`}
          {...interactiveProps}
          style={{
            transform: `translateX(${swipeOffset}px)`,
            transition: isTouching.current ? 'none' : 'transform 0.25s ease, box-shadow 0.25s ease',
            boxShadow: swipeOffset > 0 ? '0 14px 30px rgba(15, 23, 42, 0.18)' : undefined,
            position: 'relative',
            zIndex: 1,
          }}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onTouchCancel={handleTouchCancel}
        >
          <div className="mb-1 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 min-w-0">
              {isAssigned && (
                <div
                  className="h-8 w-1 shrink-0 rounded-full"
                  style={{ backgroundColor: '#3B82F6' }}
                  aria-hidden="true"
                />
              )}
              <h3 className="font-bold text-lg truncate">{unit.unit}</h3>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {notification && (
                <span
                  className="inline-flex items-center rounded-full px-2 py-1.5"
                  style={{
                    backgroundColor: UI_PALETTE.warningBg,
                  }}
                  title="Активное уведомление"
                >
                  <img
                    src="/assets/bell.svg"
                    alt=""
                    aria-hidden="true"
                    className="h-5 w-5"
                    style={{ filter: BELL_AMBER_FILTER }}
                  />
                </span>
              )}
            </div>
          </div>
          {isCritical && errorGroups.length > 0 ? (
            <UnitErrorBoard groups={errorGroups} />
          ) : (
            <p
              className={`text-sm mb-3 italic ${isPending || isOffline ? 'text-gray-400' : 'text-gray-500'}`}
            >
              {unit.event}
              {unit.cameraRead != null && unit.cameraUnread != null && (
                <>
                  {' • '}
                  {unit.cameraRead} / {unit.cameraUnread}
                </>
              )}
            </p>
          )}
        </div>
      </div>

      <ConfirmationOverlay
        open={overlayOpen}
        title={isActiveByMe ? 'Снять уведомление?' : 'Отправить последнюю партию?'}
        subtitle={
          isActiveByMe
            ? 'Уведомление о последней партии будет снято'
            : 'Данные будут направлены ответственным сотрудникам'
        }
        confirmLabel={isActiveByMe ? 'Снять' : 'Отправить'}
        cancelLabel="Отмена"
        onConfirm={handleConfirm}
        onCancel={handleCancel}
        confirmColor="blue"
      />
    </>
  );
}

export default UnitCard;
