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
 * - жест начинается строго из левой зоны карточки
 *   ({@link SWIPE_ACTIVATION_RATIO}); свайп из центра игнорируется;
 * - карточка движется с сопротивлением ({@link SWIPE_RESISTANCE}) — свайп
 *   требует осознанного движения, случайные касания не срабатывают;
 * - слева под карточкой растёт синяя «пилюля» с иконкой колокольчика
 *   и подписью действия;
 * - карточка жёстко ограничена «точкой невозврата»
 *   ({@link SWIPE_COMMIT_RATIO}) — дальше неё не двигается; при достижении
 *   точки срабатывает лёгкий тактильный отклик (вибрация);
 * - отпускание ДО точки — карточка возвращается в исходное положение;
 * - отпускание В точке — открывается overlay подтверждения, а карточка
 *   возвращается в исходное положение.
 */

interface Props {
  unit: Unit;
  alerts: Map<string, AlertData>;
  /** Активные производственные уведомления (из AppContext). */
  notifications?: Map<string, NotificationData>;
  onClick: () => void;
}

/** Доля ширины карточки слева, из которой должен начинаться свайп. */
const SWIPE_ACTIVATION_RATIO = 0.3;
/** «Точка невозврата» — доля ширины карточки (позиция как на iOS-референсе). */
const SWIPE_COMMIT_RATIO = 0.55;
/** Сопротивление движению карточки (< 1) — визуально «утяжеляет» свайп. */
const SWIPE_RESISTANCE = 0.7;
/** Минимальное смещение, после которого тап по карточке игнорируется. */
const SWIPE_CLICK_GUARD_PX = 10;
/** Длительность тактильного отклика при достижении «точки невозврата», мс. */
const HAPTIC_MS = 10;
/** easeOutCubic — плавный «мягкий» возврат карточки и «пилюли» после свайпа. */
const EASE_OUT_CUBIC = 'cubic-bezier(0.215, 0.61, 0.355, 1)';
/** Длительность анимации возврата после свайпа. */
const RETURN_TRANSITION = `0.45s ${EASE_OUT_CUBIC}`;

/** Цвет фокус-кольца карточки (клавиатурная навигация терминала РМ452). */
const FOCUS_RING_COLOR = '#4285f4';

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
  const cardRef = useRef<HTMLDivElement | null>(null);
  const [swipeOffset, setSwipeOffset] = useState(0);
  const [isFocused, setIsFocused] = useState(false);
  const touchStartX = useRef<number | null>(null);
  const thresholdPx = useRef(0);
  const isTouching = useRef(false);
  const hapticFired = useRef(false);
  // Защита от «сквозного» клика после свайпа (click приходит после touchend)
  const justSwiped = useRef(false);
  const keyboardSwipeFrame = useRef<number | null>(null);
  const keyboardSwipeActive = useRef(false);
  const swipeOffsetRef = useRef(0);

  // ── Confirmation overlay ───────────────────────────────────────────────
  const [overlayOpen, setOverlayOpen] = useState(false);
  const { sendLastBatch, reset: resetLastBatch } = useLastBatch();

  const handleTouchStart = useCallback(
    (e: React.TouchEvent) => {
      if (!isAssigned) return;
      const container = containerRef.current;
      if (!container) return;
      const width = container.offsetWidth;
      const x = e.touches[0].clientX;
      // Свайп активируется только из левой зоны карточки
      if (x - container.getBoundingClientRect().left > width * SWIPE_ACTIVATION_RATIO) return;
      touchStartX.current = x;
      thresholdPx.current = width * SWIPE_COMMIT_RATIO;
      hapticFired.current = false;
      isTouching.current = true;
    },
    [isAssigned]
  );

  const handleTouchMove = useCallback(
    (e: React.TouchEvent) => {
      if (!isAssigned || touchStartX.current == null) return;
      const delta = e.touches[0].clientX - touchStartX.current;
      if (delta <= 0) {
        setSwipeOffset(0);
        return;
      }
      // Сопротивление делает карточку «тяжелее»; дальше точки невозврата — не двигается
      const offset = Math.min(thresholdPx.current, delta * SWIPE_RESISTANCE);
      // Лёгкая вибрация при первом достижении точки невозврата
      if (offset >= thresholdPx.current && !hapticFired.current) {
        hapticFired.current = true;
        navigator.vibrate?.(HAPTIC_MS);
      }
      setSwipeOffset(offset);
    },
    [isAssigned]
  );

  const handleTouchEnd = useCallback(() => {
    touchStartX.current = null;
    isTouching.current = false;
    justSwiped.current = swipeOffset > SWIPE_CLICK_GUARD_PX;
    if (thresholdPx.current > 0 && swipeOffset >= thresholdPx.current) {
      // Точка невозврата: overlay открывается сразу, карточка возвращается назад
      setOverlayOpen(true);
    }
    setSwipeOffset(0);
  }, [swipeOffset]);

  const handleTouchCancel = useCallback(() => {
    touchStartX.current = null;
    isTouching.current = false;
    setSwipeOffset(0);
  }, []);

  const stopKeyboardSwipe = useCallback((commit: boolean) => {
    keyboardSwipeActive.current = false;
    if (keyboardSwipeFrame.current != null) {
      cancelAnimationFrame(keyboardSwipeFrame.current);
      keyboardSwipeFrame.current = null;
    }
    isTouching.current = false;
    const shouldCommit = commit && swipeOffsetRef.current >= thresholdPx.current;
    if (shouldCommit) setOverlayOpen(true);
    swipeOffsetRef.current = 0;
    setSwipeOffset(0);
  }, []);

  // ── Клавиатурный свайп (терминал РМ452) ────────────────────────────────
  // Удержание Enter (или стрелки вправо) на Tab-фокусированной карточке
  // выполняет действие свайпа вправо; короткое нажатие Enter — активация
  // карточки (аналог тапа).
  const ENTER_HOLD_MS = 300;
  const enterHoldTimer = useRef<number | null>(null);
  const enterSwipeStarted = useRef(false);

  const startKeyboardSwipe = useCallback(() => {
    const container = containerRef.current;
    if (!container || keyboardSwipeActive.current) return;
    thresholdPx.current = container.offsetWidth * SWIPE_COMMIT_RATIO;
    keyboardSwipeActive.current = true;
    isTouching.current = true;
    hapticFired.current = false;
    const startedAt = performance.now();

    const animate = (now: number) => {
      if (!keyboardSwipeActive.current) return;
      const nextOffset = Math.min(thresholdPx.current, (now - startedAt) * SWIPE_RESISTANCE);
      swipeOffsetRef.current = nextOffset;
      setSwipeOffset(nextOffset);
      if (nextOffset >= thresholdPx.current && !hapticFired.current) {
        hapticFired.current = true;
        navigator.vibrate?.(HAPTIC_MS);
      }
      keyboardSwipeFrame.current = requestAnimationFrame(animate);
    };
    keyboardSwipeFrame.current = requestAnimationFrame(animate);
  }, []);

  const clearEnterHold = useCallback(() => {
    if (enterHoldTimer.current != null) {
      window.clearTimeout(enterHoldTimer.current);
      enterHoldTimer.current = null;
    }
  }, []);

  const handleCardKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (event.key === 'ArrowRight') {
        if (!isAssigned || overlayOpen || event.repeat) return;
        event.preventDefault();
        startKeyboardSwipe();
        return;
      }
      if (event.key !== 'Enter' || isOffline || overlayOpen) return;
      event.preventDefault();
      if (event.repeat) return;
      enterSwipeStarted.current = false;
      if (!isAssigned) return;
      // Удержание Enter: короткое окно, после которого начинается свайп.
      // Если клавиша отпущена раньше — на keyup сработает активация (тап).
      clearEnterHold();
      enterHoldTimer.current = window.setTimeout(() => {
        enterHoldTimer.current = null;
        enterSwipeStarted.current = true;
        startKeyboardSwipe();
      }, ENTER_HOLD_MS);
    },
    [isAssigned, isOffline, overlayOpen, startKeyboardSwipe, clearEnterHold]
  );

  const handleCardKeyUp = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (event.key === 'ArrowRight') {
        event.preventDefault();
        stopKeyboardSwipe(true);
        return;
      }
      if (event.key !== 'Enter' || isOffline) return;
      event.preventDefault();
      clearEnterHold();
      if (enterSwipeStarted.current) {
        // Enter удерживался: завершаем свайп (commit, если пройдена точка невозврата)
        enterSwipeStarted.current = false;
        stopKeyboardSwipe(true);
        return;
      }
      // Короткое нажатие Enter — активация элемента, аналогично тапу
      if (!overlayOpen) {
        cardRef.current?.click();
      }
    },
    [isOffline, overlayOpen, clearEnterHold, stopKeyboardSwipe]
  );

  useEffect(() => () => stopKeyboardSwipe(false), [stopKeyboardSwipe]);

  const handleCardClick = useCallback(() => {
    // Тап сразу после свайпа не должен открывать детали автомата
    if (justSwiped.current) {
      justSwiped.current = false;
      return;
    }
    onClick();
  }, [onClick]);

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

  // Прогресс свайпа 0..1 до «точки невозврата»; «пилюля» растёт вслед за карточкой
  const threshold = thresholdPx.current;
  const revealProgress = threshold > 0 ? Math.min(1, swipeOffset / (threshold * 0.6)) : 0;
  const pillWidth = Math.max(0, Math.min(swipeOffset, threshold) - 24);

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
            aria-hidden="true"
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
                transition: isTouching.current
                  ? undefined
                  : `width ${RETURN_TRANSITION}, opacity ${RETURN_TRANSITION}`,
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
                opacity: revealProgress,
                transition: isTouching.current
                  ? undefined
                  : `width 0.3s ${EASE_OUT_CUBIC}, opacity 0.3s ${EASE_OUT_CUBIC}`,
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
          ref={cardRef}
          {...interactiveProps}
          className={`card p-4 md:h-full ${statusClass}${isOffline ? ' card-static' : ''}`}
          tabIndex={isOffline ? -1 : 0}
          onFocus={() => setIsFocused(true)}
          onBlur={() => {
            setIsFocused(false);
            clearEnterHold();
            enterSwipeStarted.current = false;
            stopKeyboardSwipe(false);
          }}
          onKeyDown={handleCardKeyDown}
          onKeyUp={handleCardKeyUp}
          onKeyPress={(event) => {
            if (event.key === 'ArrowRight') event.preventDefault();
          }}
          style={{
            transform: `translateX(${swipeOffset}px)`,
            transition: isTouching.current
              ? 'none'
              : `transform 0.3s ${EASE_OUT_CUBIC}, box-shadow 0.3s ${EASE_OUT_CUBIC}`,
            // Фокус-индикатор — inset-кольцо: обёртка карточки (overflow-hidden)
            // клиппит внешние ring/outline, поэтому выделение рисуется внутри bounds.
            boxShadow:
              [
                isFocused ? `inset 0 0 0 3px ${FOCUS_RING_COLOR}` : null,
                swipeOffset > 0 ? '0 14px 30px rgba(15, 23, 42, 0.18)' : null,
              ]
                .filter(Boolean)
                .join(', ') || undefined,
            // Глобальный :focus-visible outline тоже клиппится обёрткой — отключаем,
            // фокус показывает inset-кольцо выше.
            outline: isFocused ? 'none' : undefined,
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
