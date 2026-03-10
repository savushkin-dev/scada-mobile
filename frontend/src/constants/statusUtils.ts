import { ALERT_SEVERITY, DOMAIN_FLAGS, NO_DATA_UNIT_EVENTS, NORMAL_UNIT_EVENTS } from '../config';
import type { AlertData, DevicesStatusPayload, Unit } from '../types';

// ── Уровни статуса ────────────────────────────────────────────────────────────
/**
 * Уровни статуса карточки аппарата.
 *
 * - `'pending'`  — топология загружена, но WS ещё не прислал первый UNITS_STATUS.
 *                  Карточка серая; пользователь видит нейтральное состояние. Кликабельна.
 * - `'offline'`  — WS ответил, но устройство недоступно (нет данных от оборудования).
 *                  Карточка серая; переход на детали бессмысленен → не кликабельна.
 * - `'normal'`   — данные есть, аппарат работает штатно. Карточка зелёная.
 * - `'warning'`  — нештатное событие или Warning-алерт. Карточка жёлтая.
 * - `'critical'` — Critical-алерт. Карточка красная.
 */
export type UnitStatusLevel = 'pending' | 'offline' | 'critical' | 'warning' | 'normal';

/**
 * События, при которых аппарат считается работающим штатно.
 * Всё остальное (включая "Ошибка", "Остановлена", специфические сообщения) —
 * как минимум предупреждение.
 *
 * Пустая строка — статус ещё не получен; отображаем как normal
 * (карточка зеленеет сразу как придут данные о нормальной работе).
 */
// ── Статус аппарата (unit) ───────────────────────────────────────────────────

/**
 * Определяет уровень статуса аппарата.
 *
 * Порядок приоритетов:
 *  1. `pending`  — WS-данные ещё не пришли; алерты и event не проверяем.
 *  2. Critical-алерт из WS
 *  3. Warning-алерт из WS
 *  4. Нештатное событие из `unit.event` (любая строка вне `NORMAL_EVENTS`)
 *  5. Normal
 */
export function getUnitStatusLevel(unit: Unit, alerts: Map<string, AlertData>): UnitStatusLevel {
  // Пока WS не прислал первый UNITS_STATUS — аппарат в состоянии ожидания.
  // Нельзя считать его предупреждением из-за placeholder-значения 'Нет данных'.
  if (!unit.statusReady) return 'pending';

  // Аппарат недоступен / бэкенд не получил данных — статус неизвестен.
  // Визуально та же серая карточка, но семантически отличается от pending:
  // данные получены, устройство офлайн → переход в детали бессмысленен.
  if (NO_DATA_UNIT_EVENTS.has(unit.event)) return 'offline';

  const alert = alerts.get(String(unit.id));
  if (alert?.severity === ALERT_SEVERITY.critical) return 'critical';
  if (alert?.severity === ALERT_SEVERITY.warning) return 'warning';
  if (!NORMAL_UNIT_EVENTS.has(unit.event)) return 'warning';
  return 'normal';
}

// ── Статус цеха (workshop) ────────────────────────────────────────────────────

export type WorkshopStatusLevel = 'critical' | 'warning' | 'none';

/**
 * Вычисляет уровень проблемности цеха по его алертам.
 * Critical хотя бы одного аппарата → critical; Warning → warning; иначе none.
 */
export function getWorkshopStatusLevel(
  workshopId: string,
  alerts: Map<string, AlertData>
): WorkshopStatusLevel {
  let hasCritical = false;
  let hasWarning = false;
  alerts.forEach((alert) => {
    if (alert.workshopId === workshopId) {
      if (alert.severity === ALERT_SEVERITY.critical) hasCritical = true;
      else if (alert.severity === ALERT_SEVERITY.warning) hasWarning = true;
    }
  });
  if (hasCritical) return 'critical';
  if (hasWarning) return 'warning';
  return 'none';
}

// ── CSS-классы ────────────────────────────────────────────────────────────────

/** CSS-класс карточки аппарата по уровню статуса. */
export const UNIT_STATUS_CLASS: Record<UnitStatusLevel, string> = {
  pending: 'status-pending',
  offline: 'status-pending', // тот же серый цвет, но card-static добавляется в UnitCard
  critical: 'status-critical',
  warning: 'status-warning',
  normal: 'status-normal',
};

/** CSS-класс карточки цеха по уровню статуса. */
export const WORKSHOP_STATUS_CLASS: Record<WorkshopStatusLevel, string> = {
  critical: 'status-critical',
  warning: 'status-warning',
  none: 'status-normal',
};

// ── Статус устройства (device) ────────────────────────────────────────────────

/**
 * Уровни статуса карточки устройства.
 *
 * - `'pending'` — WS-данные ещё не пришли; статус неизвестен. Карточка серая.
 * - `'error'`   — устройство сообщило об ошибке. Карточка красная.
 * - `'working'` — устройство активно работает. Карточка зелёная.
 * - `'stopped'` — данные есть, но устройство не активно и без ошибок. Карточка стандартная.
 */
export type DeviceStatusLevel = 'pending' | 'error' | 'working' | 'stopped';

/**
 * Определяет уровень статуса устройства по имени и текущим WS-данным.
 *
 * Если `wsData === null` — WS ещё не прислал ни одного `DEVICES_STATUS` → `pending`.
 * Если устройство не нашлось в пейлоаде → считаем остановленным (`stopped`).
 */
export function getDeviceStatusLevel(
  wsData: DevicesStatusPayload | null,
  name: string
): DeviceStatusLevel {
  if (wsData === null) return 'pending';
  const info = wsData[name];
  if (!info) return 'stopped';
  if (info.error === DOMAIN_FLAGS.active) return 'error';
  if (info.state === DOMAIN_FLAGS.active) return 'working';
  return 'stopped';
}

/** CSS-класс карточки устройства по уровню статуса. */
export const DEVICE_STATUS_CLASS: Record<DeviceStatusLevel, string> = {
  pending: 'status-pending', // серый — WS ещё не ответил
  error: 'status-critical', // красный — ошибка устройства
  working: 'status-normal', // зелёный — устройство работает
  stopped: '', // дефолтный белый — не активно, без ошибок
};
