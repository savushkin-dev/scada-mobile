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

export const AlertSeveritySchema = z.enum(['Critical', 'Warning']);

// ── /ws/live — входящие сообщения ─────────────────────────────────────

export const AlertWsMessageSchema = z.object({
  type: z.literal('ALERT'),
  workshopId: z.string(),
  unitId: z.union([z.string(), z.number()]),
  severity: AlertSeveritySchema,
  active: z.boolean(),
  errors: z.array(z.unknown()),
  timestamp: z.string(),
});

/** Элемент payload для UNITS_STATUS */
export const UnitStatusSchema = z.object({
  unitId: z.string(),
  workshopId: z.string(),
  event: z.string(),
  timer: z.string(),
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

/**
 * Discriminated union всех входящих сообщений /ws/live.
 * Неизвестный type отбрасывается через safeParse (форвард-совместимость).
 */
export const LiveWsIncomingMessageSchema = z.discriminatedUnion('type', [
  AlertSnapshotMessageSchema,
  UnitsStatusMessageSchema,
  AlertWsMessageSchema,
]);

// ── /ws/unit/{unitId} — входящие сообщения ────────────────────────────

export const LineStatusPayloadSchema = z.object({
  lineName: z.string().optional(),
  lineState: z.number().optional(),
  shortCode: z.string().optional(),
  description: z.string().optional(),
  ean13: z.string().optional(),
  batchNumber: z.string().optional(),
  dateProduced: z.string().optional(),
  datePacking: z.string().optional(),
  dateExpiration: z.string().optional(),
  initialCounter: z.number().optional(),
  site: z.string().optional(),
  itf: z.string().optional(),
  capacity: z.number().optional(),
  boxCount: z.number().optional(),
  packageCount: z.number().optional(),
  freeze: z.number().optional(),
  region: z.string().optional(),
  design: z.number().optional(),
  printDM: z.number().optional(),
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
export type AlertWsMessage = z.infer<typeof AlertWsMessageSchema>;
export type UnitStatus = z.infer<typeof UnitStatusSchema>;
export type UnitsStatusMessage = z.infer<typeof UnitsStatusMessageSchema>;
export type AlertSnapshotMessage = z.infer<typeof AlertSnapshotMessageSchema>;
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
