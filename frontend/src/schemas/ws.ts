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
  workshopId: z.number().int().positive(),
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
  workshopId: z.number().int().positive(),
  event: z.string(),
  cameraRead: z.string().nullish(),
  cameraUnread: z.string().nullish(),
});

export const UnitsStatusMessageSchema = z.object({
  type: z.literal('UNITS_STATUS'),
  workshopId: z.number().int().positive(),
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
  creatorName: z.string().nullable().optional(),
  eventType: z.string().nullable().optional(),
  active: z.boolean(),
  timestamp: z.string().nullable(),
});

/** NOTIFICATION_SNAPSHOT — начальный срез при WS-коннекте */
export const NotificationSnapshotMessageSchema = z.object({
  type: z.literal('NOTIFICATION_SNAPSHOT'),
  payload: z.array(NotificationWsMessageSchema),
});

// ── /ws/live — User assignments ────────────────────────────────────────

export const UserAssignmentsMessageSchema = z.object({
  type: z.literal('USER_ASSIGNMENTS'),
  payload: z.array(
    z.object({
      unitId: z.union([z.string(), z.number()]),
      printsrvInstanceId: z.string().nullable().optional(),
      unitName: z.string().optional(),
    })
  ),
});

// ── /ws/live — Admin data changes ──────────────────────────────────────

export const ChangeActionSchema = z.enum(['CREATE', 'UPDATE', 'DELETE']);

export const EmployeePayloadSchema = z.object({
  id: z.union([z.string(), z.number()]),
  fullName: z.string().nullable(),
  code: z.string().nullable(),
  roleId: z.union([z.string(), z.number()]).nullable(),
  active: z.boolean(),
});

export const EmployeeChangedMessageSchema = z.object({
  type: z.literal('EMPLOYEE_CHANGED'),
  action: ChangeActionSchema,
  payload: EmployeePayloadSchema.nullable(),
});

export const WorkshopPayloadSchema = z.object({
  id: z.number(),
  name: z.string().nullable(),
  active: z.boolean(),
  totalUnits: z.number(),
});

export const WorkshopChangedMessageSchema = z.object({
  type: z.literal('WORKSHOP_CHANGED'),
  action: ChangeActionSchema,
  payload: WorkshopPayloadSchema.nullable(),
});

export const RolePayloadSchema = z.object({
  id: z.number(),
  name: z.string().nullable(),
});

export const RoleChangedMessageSchema = z.object({
  type: z.literal('ROLE_CHANGED'),
  action: ChangeActionSchema,
  payload: RolePayloadSchema.nullable(),
});

export const UnitPayloadSchema = z.object({
  id: z.union([z.string(), z.number()]),
  printsrvInstanceId: z.string().nullable(),
  workshopId: z.number(),
  name: z.string().nullable(),
  active: z.boolean(),
});

export const UnitChangedMessageSchema = z.object({
  type: z.literal('UNIT_CHANGED'),
  action: ChangeActionSchema,
  payload: UnitPayloadSchema.nullable(),
});

export const DevicePayloadSchema = z.object({
  id: z.union([z.string(), z.number()]),
  unitId: z.union([z.string(), z.number()]).nullable(),
  printsrvInstanceId: z.string().nullable(),
  catalogId: z.union([z.string(), z.number()]).nullable(),
});

export const DeviceChangedMessageSchema = z.object({
  type: z.literal('DEVICE_CHANGED'),
  action: ChangeActionSchema,
  payload: DevicePayloadSchema.nullable(),
});

export const DeviceCatalogPayloadSchema = z.object({
  id: z.number(),
  code: z.string().nullable(),
  name: z.string().nullable(),
  typeId: z.union([z.string(), z.number()]).nullable(),
  active: z.boolean(),
});

export const DeviceCatalogChangedMessageSchema = z.object({
  type: z.literal('DEVICE_CATALOG_CHANGED'),
  action: ChangeActionSchema,
  payload: DeviceCatalogPayloadSchema.nullable(),
});

export const DeviceTypePayloadSchema = z.object({
  id: z.number(),
  code: z.string().nullable(),
  name: z.string().nullable(),
});

export const DeviceTypeChangedMessageSchema = z.object({
  type: z.literal('DEVICE_TYPE_CHANGED'),
  action: ChangeActionSchema,
  payload: DeviceTypePayloadSchema.nullable(),
});

export const UserNotificationSettingsPayloadSchema = z.object({
  id: z.number(),
  userId: z.union([z.string(), z.number()]).nullable(),
  unitId: z.union([z.string(), z.number()]).nullable(),
  incidentNotificationsEnabled: z.boolean(),
  androidCallNotificationsEnabled: z.boolean(),
  active: z.boolean(),
});

export const UserNotificationSettingsChangedMessageSchema = z.object({
  type: z.literal('USER_NOTIFICATION_SETTINGS_CHANGED'),
  action: ChangeActionSchema,
  payload: UserNotificationSettingsPayloadSchema.nullable(),
});

export const ForceLogoutMessageSchema = z.object({
  type: z.literal('FORCE_LOGOUT'),
  reason: z.string(),
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
  UserAssignmentsMessageSchema,
  EmployeeChangedMessageSchema,
  WorkshopChangedMessageSchema,
  RoleChangedMessageSchema,
  UnitChangedMessageSchema,
  DeviceChangedMessageSchema,
  DeviceCatalogChangedMessageSchema,
  DeviceTypeChangedMessageSchema,
  UserNotificationSettingsChangedMessageSchema,
  ForceLogoutMessageSchema,
]);

// ── Выводимые типы /ws/live ───────────────────────────────────────────

export type ChangeAction = z.infer<typeof ChangeActionSchema>;

export type EmployeePayload = z.infer<typeof EmployeePayloadSchema>;
export type EmployeeChangedMessage = z.infer<typeof EmployeeChangedMessageSchema>;

export type WorkshopPayload = z.infer<typeof WorkshopPayloadSchema>;
export type WorkshopChangedMessage = z.infer<typeof WorkshopChangedMessageSchema>;

export type RolePayload = z.infer<typeof RolePayloadSchema>;
export type RoleChangedMessage = z.infer<typeof RoleChangedMessageSchema>;

export type UnitPayload = z.infer<typeof UnitPayloadSchema>;
export type UnitChangedMessage = z.infer<typeof UnitChangedMessageSchema>;

export type DevicePayload = z.infer<typeof DevicePayloadSchema>;
export type DeviceChangedMessage = z.infer<typeof DeviceChangedMessageSchema>;

export type DeviceCatalogPayload = z.infer<typeof DeviceCatalogPayloadSchema>;
export type DeviceCatalogChangedMessage = z.infer<typeof DeviceCatalogChangedMessageSchema>;

export type DeviceTypePayload = z.infer<typeof DeviceTypePayloadSchema>;
export type DeviceTypeChangedMessage = z.infer<typeof DeviceTypeChangedMessageSchema>;

export type UserNotificationSettingsPayload = z.infer<typeof UserNotificationSettingsPayloadSchema>;
export type UserNotificationSettingsChangedMessage = z.infer<
  typeof UserNotificationSettingsChangedMessageSchema
>;

export type ForceLogoutMessage = z.infer<typeof ForceLogoutMessageSchema>;

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
  cameraRead: z.string().nullish(),
  cameraUnread: z.string().nullish(),
});

export const DevicesStatusWsPrinterSchema = z.object({
  deviceName: z.string(),
  st: z.string().nullable(),
  error: z.string().nullable(),
  batch: z.string().nullable(),
  disconnected: z.boolean().optional(),
});

export const DevicesStatusWsCameraSchema = z.object({
  deviceName: z.string(),
  read: z.string().nullable(),
  unread: z.string().nullable(),
  st: z.string().nullable(),
  error: z.string().nullable(),
  disconnected: z.boolean().optional(),
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
export type UserAssignmentsMessage = z.infer<typeof UserAssignmentsMessageSchema>;
export type UserAssignmentsPayload = UserAssignmentsMessage['payload'];
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
