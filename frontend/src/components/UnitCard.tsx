import { useCallback, useRef, useState } from 'react';
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
 * Свайп вправо (только для закреплённых автоматов):
 * - карточка сдвигается вправо;
 * - слева под ней появляется статический синий эмбиент с иконкой колокольчика;
 * - тап по эмбиенту вызывает overlay подтверждения.
 */

interface Props {
  unit: Unit;
  alerts: Map<string, AlertData>;
  /** Активные производственные уведомления (из AppContext). */
  notifications?: Map<string, NotificationData>;
  onClick: () => void;
}

const SWIPE_THRESHOLD_PX = 80;
const SWIPE_MAX_PX = 140;

/** CSS filter для перекраски bell.svg в белый цвет. */
const BELL_WHITE_FILTER =
  'brightness(0) saturate(100%) invert(100%) sepia(0%) saturate(0%) hue-rotate(0deg) brightness(100%) contrast(100%)';

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
  // offline: карточка некликабельна; card-static отключает cursor:pointer и :active-scale.
  const interactiveProps = isOffline
    ? { 'aria-disabled': true as const }
    : { onClick, role: 'button' as const };

  const errorGroups = isCritical ? getUnitErrorGroups(unit.id, alerts) : [];

  // ── Swipe state ────────────────────────────────────────────────────────
  const [swipeOffset, setSwipeOffset] = useState(0);
  const touchStartX = useRef<number | null>(null);
  const currentOffset = useRef(0);
  const isTouching = useRef(false);

  // ── Confirmation overlay ───────────────────────────────────────────────
  const [overlayOpen, setOverlayOpen] = useState(false);
  const { sendLastBatch, reset: resetLastBatch } = useLastBatch();

  const handleTouchStart = useCallback(
    (e: React.TouchEvent) => {
      if (!isAssigned) return;
      touchStartX.current = e.touches[0].clientX;
      currentOffset.current = swipeOffset;
      isTouching.current = true;
    },
    [isAssigned, swipeOffset]
  );

  const handleTouchMove = useCallback(
    (e: React.TouchEvent) => {
      if (!isAssigned || touchStartX.current == null) return;
      const delta = e.touches[0].clientX - touchStartX.current;
      // Свайп вправо (delta > 0) → карточка сдвигается ВПРАВО (положительный offset)
      if (delta > 0) {
        const offset = Math.min(SWIPE_MAX_PX, Math.max(0, currentOffset.current + delta));
        setSwipeOffset(offset);
      } else if (delta < 0) {
        // Свайп влево — возвращаем карточку
        const offset = Math.max(0, currentOffset.current + delta);
        setSwipeOffset(offset);
      }
    },
    [isAssigned]
  );

  const handleTouchEnd = useCallback(() => {
    touchStartX.current = null;
    isTouching.current = false;
    if (swipeOffset > SWIPE_THRESHOLD_PX) {
      setSwipeOffset(SWIPE_MAX_PX);
    } else {
      setSwipeOffset(0);
    }
  }, [swipeOffset]);

  const handleRevealClick = useCallback(() => {
    setOverlayOpen(true);
  }, []);

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

  // Прогресс свайпа 0..1 для анимации фона
  const swipeProgress = Math.min(1, swipeOffset / SWIPE_MAX_PX);

  return (
    <>
      <div
        className="relative select-none overflow-hidden rounded-[24px]"
        style={{ touchAction: 'pan-y' }}
      >
        {/* Фоновый слой с эмбиентом и колокольчиком — статичен, карточка сдвигается поверх */}
        {isAssigned && (
          <div
            className="absolute inset-0 flex items-center justify-start rounded-[24px]"
            style={{
              zIndex: 0,
              opacity: swipeProgress,
              transition: isTouching.current ? undefined : 'opacity 0.25s ease',
              background:
                'radial-gradient(ellipse 55% 100% at 70px 50%, rgba(59,130,246,0.55) 0%, rgba(59,130,246,0.22) 40%, rgba(59,130,246,0.06) 70%, transparent 100%)',
            }}
            onClick={handleRevealClick}
            role="button"
            aria-label={isActiveByMe ? 'Снять уведомление' : 'Последняя партия'}
          >
            <div
              className="flex items-center justify-center"
              style={{ width: `${SWIPE_MAX_PX}px`, flexShrink: 0 }}
            >
              <img
                src={isActiveByMe ? '/assets/bell-off.svg' : '/assets/bell.svg'}
                alt=""
                aria-hidden="true"
                className="h-8 w-8"
                style={{ filter: BELL_WHITE_FILTER }}
              />
            </div>
          </div>
        )}

        {/* Карточка (сдвигается вправо при свайпе) */}
        <div
          className={`card p-4 md:h-full ${statusClass}${isOffline ? ' card-static' : ''}`}
          {...interactiveProps}
          style={{
            transform: `translateX(${swipeOffset}px)`,
            transition: isTouching.current ? 'none' : 'transform 0.25s ease',
            position: 'relative',
            zIndex: 1,
          }}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
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
                    src={isActiveByMe ? '/assets/bell-off.svg' : '/assets/bell.svg'}
                    alt=""
                    aria-hidden="true"
                    className="h-5 w-5"
                    style={{ filter: BELL_WHITE_FILTER }}
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
