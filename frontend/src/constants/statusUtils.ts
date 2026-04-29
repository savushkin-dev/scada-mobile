import { DOMAIN_DEFAULTS, DOMAIN_FLAGS } from '../config';
import type { AlertData, AlertError, DevicesStatusPayload, Unit } from '../types';

// ── Уровни статуса ────────────────────────────────────────────────────────────
/**
 * Уровни статуса карточки аппарата.
 *
 * - `'pending'`  — топология загружена, но WS ещё не прислал первый UNITS_STATUS.
 *                  Карточка серая; пользователь видит нейтральное состояние. Кликабельна.
 * - `'offline'`  — WS ответил, но устройство недоступно (нет данных от оборудования).
 *                  Карточка серая; переход на детали бессмысленен → не кликабельна.
 * - `'normal'`   — данные есть, аппарат работает штатно. Карточка зелёная.
 * - `'critical'` — есть алёрт или нештатное событие. Карточка красная.
 */
export type UnitStatusLevel = 'pending' | 'offline' | 'critical' | 'normal';

// ── Статус аппарата (unit) ───────────────────────────────────────────────────

/**
 * Определяет уровень статуса аппарата.
 *
 * Порядок приоритетов:
 *  1. `pending`  — WS-данные ещё не пришли.
 *  2. `critical` — есть активный алёрт (единый источник правды по ошибкам).
 *  3. `offline`  — данные получены, но событие = "Нет данных".
 *  4. `normal`   — нет алёртов, данные получены.
 */
export function getUnitStatusLevel(unit: Unit, alerts: Map<string, AlertData>): UnitStatusLevel {
  if (!unit.statusReady) return 'pending';
  if (alerts.has(String(unit.id))) return 'critical';
  if (unit.event === DOMAIN_DEFAULTS.noDataEvent) return 'offline';
  return 'normal';
}

// ── Статус цеха (workshop) ────────────────────────────────────────────────────

export type WorkshopStatusLevel = 'critical' | 'none';

/**
 * Вычисляет уровень проблемности цеха по его алертам.
 * Есть хотя бы один алёрт в цехе → critical; иначе none.
 */
export function getWorkshopStatusLevel(
  workshopId: string,
  alerts: Map<string, AlertData>
): WorkshopStatusLevel {
  for (const alert of alerts.values()) {
    if (alert.workshopId === workshopId) return 'critical';
  }
  return 'none';
}

// ── CSS-классы ────────────────────────────────────────────────────────────────

/** CSS-класс карточки аппарата по уровню статуса. */
export const UNIT_STATUS_CLASS: Record<UnitStatusLevel, string> = {
  pending: 'status-pending',
  offline: 'status-pending', // тот же серый цвет, но card-static добавляется в UnitCard
  critical: 'status-critical',
  normal: 'status-normal',
};

/** CSS-класс карточки цеха по уровню статуса. */
export const WORKSHOP_STATUS_CLASS: Record<WorkshopStatusLevel, string> = {
  critical: 'status-critical',
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

// ── Помощники для отображения ошибок в карточках ──────────────────────────────

/**
 * Одна ошибка устройства — источник и описание.
 */
export interface ErrorEntry {
  /** Имя устройства-источника ("Line", "Printer11" и т.д.). */
  device: string;
  /** Текстовое описание ошибки. */
  message: string;
}

/**
 * Группа ошибок одного аппарата.
 *
 * - `unitName` — читаемое имя аппарата; **не задаётся** в контексте карточки
 *   самого аппарата (заголовок уже есть в h3). В карточке цеха задан всегда.
 * - `entries` — все активные ошибки данного аппарата.
 */
export interface ErrorGroup {
  unitName?: string;
  entries: ErrorEntry[];
}

/**
 * Возвращает группы ошибок для «табло» одного аппарата.
 * Единственная группа — без `unitName` (заголовок уже есть в карточке).
 * Пустой массив — если алёртов нет.
 */
export function getUnitErrorGroups(unitId: string, alerts: Map<string, AlertData>): ErrorGroup[] {
  const alert = alerts.get(unitId);
  if (!alert || alert.errors.length === 0) return [];
  return [
    { entries: alert.errors.map((e: AlertError) => ({ device: e.device, message: e.message })) },
  ];
}

/**
 * Возвращает сгруппированные ошибки цеха — по одной группе на каждый
 * проблемный аппарат. Порядок соответствует порядку вхождений в Map алёртов.
 */
export function getWorkshopErrorGroups(
  workshopId: string,
  alerts: Map<string, AlertData>
): ErrorGroup[] {
  const groups: ErrorGroup[] = [];
  for (const alert of alerts.values()) {
    if (alert.workshopId !== workshopId || alert.errors.length === 0) continue;
    groups.push({
      unitName: alert.unitName,
      entries: alert.errors.map((e: AlertError) => ({ device: e.device, message: e.message })),
    });
  }
  return groups;
}
