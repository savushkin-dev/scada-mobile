/**
 * Zod-схемы для всех WebSocket-сообщений.
 *
 * Две группы:
 * - LiveWsIncomingMessageSchema — discriminated union для /ws/live
 * - UnitWsMessageSchema         — discriminated union для /ws/unit/{unitId}
 *
 * Все TS-типы сообщений в types/index.ts выводятся через z.infer<>.
 */
import { z } from 'zod';

// ── Общие ─────────────────────────────────────────────────────────────

export const AlertSeveritySchema = z.literal('Critical');

/**
 * Описание одной конкретной ошибки в составе алёрта.
 * Соответствует бэкендовому AlertErrorDTO.
 */
export const AlertErrorSchema = z.object({
  /** Имя устройства-источника: "Line", "Printer11", "CamAgregation" и т.п. */
  device: z.string(),
  /** Числовой код ошибки из протокола PrintSrv (0 если не применимо). */
  code: z.number().int(),
  /** Текстовое описание ошибки. */
  message: z.string(),
});

// ── /ws/live — входящие сообщения ─────────────────────────────────────

export const AlertWsMessageSchema = z.object({
  type: z.literal('ALERT'),
  workshopId: z.string(),
  unitId: z.union([z.string(), z.number()]),
  /** Читаемое название аппарата (для отображения в карточках). */
  unitName: z.string(),
  severity: AlertSeveritySchema,
  active: z.boolean(),
  errors: z.array(AlertErrorSchema),
  timestamp: z.string(),
});

/** Элемент payload для UNITS_STATUS */
export const UnitStatusSchema = z.object({
  unitId: z.string(),
  workshopId: z.string(),
  event: z.string(),
});

export const UnitsStatusMessageSchema = z.object({
  type: z.literal('UNITS_STATUS'),
  workshopId: z.string(),
  payload: z.array(UnitStatusSchema),
});

export const AlertSnapshotMessageSchema = z.object({
  type: z.literal('ALERT_SNAPSHOT'),
  payload: z.array(AlertWsMessageSchema),
});

// ── /ws/live — Notifications (производственные уведомления) ───────────

/**
 * Дельта-сообщение NOTIFICATION — появление или снятие пользовательского
 * производственного уведомления.
 * <p>
 * В отличие от ALERT (автоматический, на основе scada-данных),
 * NOTIFICATION создаётся работником явным нажатием FAB.
 */
export const NotificationWsMessageSchema = z.object({
  type: z.literal('NOTIFICATION'),
  unitId: z.string(),
  unitName: z.string(),
  creatorId: z.string().nullable(),
  active: z.boolean(),
  timestamp: z.string().nullable(),
});

/** NOTIFICATION_SNAPSHOT — начальный срез при WS-коннекте */
export const NotificationSnapshotMessageSchema = z.object({
  type: z.literal('NOTIFICATION_SNAPSHOT'),
  payload: z.array(NotificationWsMessageSchema),
});

/**
 * Discriminated union всех входящих сообщений /ws/live.
 * Неизвестный type отбрасывается через safeParse (форвард-совместимость).
 */
export const LiveWsIncomingMessageSchema = z.discriminatedUnion('type', [
  AlertSnapshotMessageSchema,
  NotificationSnapshotMessageSchema,
  UnitsStatusMessageSchema,
  AlertWsMessageSchema,
  NotificationWsMessageSchema,
]);

// ── /ws/unit/{unitId} — входящие сообщения ────────────────────────────

// Бэкенд (PrintSrv → Spring) передаёт все поля payload как JSON-строки,
// включая числовые и булевы флаги ("0"/"1"). Незаполненные поля приходят
// как JSON null (Jackson без NON_NULL). nullish() = string | null | undefined.
export const LineStatusPayloadSchema = z.object({
  lineName: z.string().nullish(),
  lineState: z.string().nullish(),
  shortCode: z.string().nullish(),
  description: z.string().nullish(),
  ean13: z.string().nullish(),
  batchNumber: z.string().nullish(),
  dateProduced: z.string().nullish(),
  datePacking: z.string().nullish(),
  dateExpiration: z.string().nullish(),
  initialCounter: z.string().nullish(),
  site: z.string().nullish(),
  itf: z.string().nullish(),
  capacity: z.string().nullish(),
  boxCount: z.string().nullish(),
  packageCount: z.string().nullish(),
  freeze: z.string().nullish(),
  region: z.string().nullish(),
  design: z.string().nullish(),
  printDM: z.string().nullish(),
});

export const DevicesStatusWsPrinterSchema = z.object({
  deviceName: z.string(),
  state: z.string().nullable(),
  error: z.string().nullable(),
  batch: z.string().nullable(),
});

export const DevicesStatusWsCameraSchema = z.object({
  deviceName: z.string(),
  read: z.string().nullable(),
  unread: z.string().nullable(),
  state: z.string().nullable(),
  error: z.string().nullable(),
});

export const DevicesStatusWsPayloadSchema = z.object({
  printers: z.array(DevicesStatusWsPrinterSchema),
  aggregationCams: z.array(DevicesStatusWsCameraSchema),
  aggregationBoxCams: z.array(DevicesStatusWsCameraSchema),
  checkerCams: z.array(DevicesStatusWsCameraSchema),
});

export const QueueItemSchema = z.object({
  position: z.number(),
  shortCode: z.string(),
  batch: z.string(),
  dateProduced: z.string(),
});

export const QueuePayloadSchema = z.object({
  items: z.array(QueueItemSchema),
});

export const DeviceErrorSchema = z.object({
  objectName: z.string(),
  propertyDesc: z.string(),
  value: z.string(),
  description: z.string().optional(),
});

export const LogEntrySchema = z.object({
  time: z.string(),
  ackTime: z.string(),
  group: z.string(),
  description: z.string(),
});

export const ErrorsPayloadSchema = z.object({
  deviceErrors: z.array(DeviceErrorSchema).optional(),
  logs: z.array(LogEntrySchema).optional(),
});

/**
 * Discriminated union всех входящих сообщений /ws/unit/{unitId}.
 */
export const UnitWsMessageSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal('LINE_STATUS'), payload: LineStatusPayloadSchema }),
  z.object({ type: z.literal('DEVICES_STATUS'), payload: DevicesStatusWsPayloadSchema }),
  z.object({ type: z.literal('QUEUE'), payload: QueuePayloadSchema }),
  z.object({ type: z.literal('ERRORS'), payload: ErrorsPayloadSchema }),
]);

// ── Выводимые типы ────────────────────────────────────────────────────

export type AlertSeverity = z.infer<typeof AlertSeveritySchema>;
export type AlertError = z.infer<typeof AlertErrorSchema>;
export type AlertWsMessage = z.infer<typeof AlertWsMessageSchema>;
export type UnitStatus = z.infer<typeof UnitStatusSchema>;
export type UnitsStatusMessage = z.infer<typeof UnitsStatusMessageSchema>;
export type AlertSnapshotMessage = z.infer<typeof AlertSnapshotMessageSchema>;
export type NotificationWsMessage = z.infer<typeof NotificationWsMessageSchema>;
export type NotificationSnapshotMessage = z.infer<typeof NotificationSnapshotMessageSchema>;
export type LiveWsIncomingMessage = z.infer<typeof LiveWsIncomingMessageSchema>;
export type LineStatusPayload = z.infer<typeof LineStatusPayloadSchema>;
export type DevicesStatusWsPrinter = z.infer<typeof DevicesStatusWsPrinterSchema>;
export type DevicesStatusWsCamera = z.infer<typeof DevicesStatusWsCameraSchema>;
export type DevicesStatusWsPayload = z.infer<typeof DevicesStatusWsPayloadSchema>;
export type QueueItem = z.infer<typeof QueueItemSchema>;
export type QueuePayload = z.infer<typeof QueuePayloadSchema>;
export type DeviceError = z.infer<typeof DeviceErrorSchema>;
export type LogEntry = z.infer<typeof LogEntrySchema>;
export type ErrorsPayload = z.infer<typeof ErrorsPayloadSchema>;
export type UnitWsMessage = z.infer<typeof UnitWsMessageSchema>;
