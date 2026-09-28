import { describe, expect, it } from 'vitest';
import {
  AlertErrorSchema,
  AlertWsMessageSchema,
  AlertSnapshotMessageSchema,
  AdminNotificationMessageSchema,
  DeviceErrorSchema,
  DevicesStatusWsPayloadSchema,
  EmployeeChangedMessageSchema,
  EmployeePayloadSchema,
  ForceLogoutMessageSchema,
  LineStatusPayloadSchema,
  LiveWsIncomingMessageSchema,
  LogEntrySchema,
  NotificationSnapshotMessageSchema,
  NotificationWsMessageSchema,
  QueueItemSchema,
  QueuePayloadSchema,
  UnitsStatusMessageSchema,
  UnitWsMessageSchema,
  UserAssignmentsMessageSchema,
} from './ws';

const validAlert = {
  type: 'ALERT',
  workshopId: 5,
  unitId: 'Printer11',
  unitName: 'Принтер 11',
  severity: 'Critical',
  active: true,
  errors: [{ device: 'Line', code: 17, message: 'Ошибка датчика' }],
  timestamp: '2026-09-25T10:00:00',
};

describe('AlertErrorSchema', () => {
  it('валидная ошибка парсится', () => {
    expect(
      AlertErrorSchema.parse({ device: 'CamAgregation', code: 3, message: 'нет связи' })
    ).toEqual({
      device: 'CamAgregation',
      code: 3,
      message: 'нет связи',
    });
  });

  it('код не integer — отказ', () => {
    expect(AlertErrorSchema.safeParse({ device: 'Line', code: 1.5, message: 'x' }).success).toBe(
      false
    );
  });

  it('отсутствует message — отказ', () => {
    expect(AlertErrorSchema.safeParse({ device: 'Line', code: 1 }).success).toBe(false);
  });
});

describe('AlertWsMessageSchema', () => {
  it('валидный ALERT парсится', () => {
    expect(AlertWsMessageSchema.parse(validAlert).severity).toBe('Critical');
  });

  it('unitId как число допустим', () => {
    expect(AlertWsMessageSchema.parse({ ...validAlert, unitId: 42 }).unitId).toBe(42);
  });

  it('severity не Critical — отказ', () => {
    expect(AlertWsMessageSchema.safeParse({ ...validAlert, severity: 'Warning' }).success).toBe(
      false
    );
  });

  it('отсутствует unitName — отказ', () => {
    const { unitName: _omit, ...broken } = validAlert;
    expect(AlertWsMessageSchema.safeParse(broken).success).toBe(false);
  });

  it('unitDbId опционален: без него и с null парсится', () => {
    expect(AlertWsMessageSchema.parse(validAlert).unitDbId).toBeUndefined();
    expect(AlertWsMessageSchema.parse({ ...validAlert, unitDbId: null }).unitDbId).toBeNull();
    expect(AlertWsMessageSchema.parse({ ...validAlert, unitDbId: 7 }).unitDbId).toBe(7);
  });
});

describe('UnitsStatusMessageSchema / UnitStatusSchema', () => {
  const base = {
    type: 'UNITS_STATUS',
    workshopId: 5,
    payload: [
      { unitId: 'Line', workshopId: 5, event: 'WORK', cameraRead: '12', cameraUnread: '3' },
    ],
  };

  it('валидное сообщение парсится', () => {
    expect(UnitsStatusMessageSchema.parse(base).payload).toHaveLength(1);
  });

  it('cameraRead/cameraUnread опциональны: без них и с null парсится', () => {
    const { cameraRead: _r, cameraUnread: _u, ...item } = base.payload[0];
    const msg = { ...base, payload: [item] };
    expect(UnitsStatusMessageSchema.safeParse(msg).success).toBe(true);
    const withNull = {
      ...base,
      payload: [{ ...item, cameraRead: null, cameraUnread: null }],
    };
    expect(UnitsStatusMessageSchema.parse(withNull).payload[0].cameraRead).toBeNull();
  });

  it('workshopId не positive int — отказ', () => {
    expect(UnitsStatusMessageSchema.safeParse({ ...base, workshopId: 0 }).success).toBe(false);
  });
});

describe('NotificationWsMessageSchema', () => {
  const base = {
    type: 'NOTIFICATION',
    unitId: 'Printer11',
    unitName: 'Принтер 11',
    creatorId: 'user-1',
    active: true,
    timestamp: '2026-09-25T10:00:00',
  };

  it('минимальный объект без опциональных полей парсится', () => {
    expect(NotificationWsMessageSchema.parse(base).active).toBe(true);
  });

  it('creatorId: null и полный набор полей парсится', () => {
    const full = {
      ...base,
      creatorId: null,
      creatorName: 'Иван',
      eventType: 'CALL',
      notificationId: 9,
      status: 'PENDING',
      acceptedBy: null,
      acceptedAt: null,
      version: 2,
      curItem: '1605 | 328 | 25.09.2026',
    };
    const parsed = NotificationWsMessageSchema.parse(full);
    expect(parsed.notificationId).toBe(9);
    expect(parsed.curItem).toBe('1605 | 328 | 25.09.2026');
  });

  it('недопустимый status — отказ', () => {
    expect(NotificationWsMessageSchema.safeParse({ ...base, status: 'UNKNOWN' }).success).toBe(
      false
    );
  });

  it('отсутствует active — отказ', () => {
    const { active: _omit, ...broken } = base;
    expect(NotificationWsMessageSchema.safeParse(broken).success).toBe(false);
  });
});

describe('NotificationSnapshotMessageSchema', () => {
  it('пустой срез парсится', () => {
    expect(
      NotificationSnapshotMessageSchema.parse({ type: 'NOTIFICATION_SNAPSHOT', payload: [] })
        .payload
    ).toHaveLength(0);
  });

  it('элемент с payload-невалидным элементом — отказ', () => {
    expect(
      NotificationSnapshotMessageSchema.safeParse({
        type: 'NOTIFICATION_SNAPSHOT',
        payload: [{ type: 'NOTIFICATION' }],
      }).success
    ).toBe(false);
  });
});

describe('UserAssignmentsMessageSchema', () => {
  it('минимальный элемент (только unitId) парсится', () => {
    const msg = { type: 'USER_ASSIGNMENTS', payload: [{ unitId: 7 }] };
    expect(UserAssignmentsMessageSchema.parse(msg).payload[0]).toEqual({ unitId: 7 });
  });

  it('отсутствует unitId — отказ', () => {
    expect(
      UserAssignmentsMessageSchema.safeParse({ type: 'USER_ASSIGNMENTS', payload: [{}] }).success
    ).toBe(false);
  });
});

describe('Admin-сообщения изменений', () => {
  it('EmployeeChangedMessageSchema: CREATE с payload парсится', () => {
    const payload: unknown = {
      id: 'user-1',
      fullName: 'Иван Петров',
      code: '1024',
      roleId: 2,
      active: true,
    };
    expect(EmployeePayloadSchema.parse(payload).fullName).toBe('Иван Петров');
    const msg = EmployeeChangedMessageSchema.parse({
      type: 'EMPLOYEE_CHANGED',
      action: 'CREATE',
      payload,
    });
    expect(msg.action).toBe('CREATE');
  });

  it('EmployeeChangedMessageSchema: DELETE с payload null парсится', () => {
    expect(
      EmployeeChangedMessageSchema.parse({
        type: 'EMPLOYEE_CHANGED',
        action: 'DELETE',
        payload: null,
      }).payload
    ).toBeNull();
  });

  it('недопустимый action — отказ', () => {
    expect(
      EmployeeChangedMessageSchema.safeParse({
        type: 'EMPLOYEE_CHANGED',
        action: 'PATCH',
        payload: null,
      }).success
    ).toBe(false);
  });
});

describe('ForceLogoutMessageSchema / AdminNotificationMessageSchema', () => {
  it('FORCE_LOGOUT парсится', () => {
    expect(
      ForceLogoutMessageSchema.parse({ type: 'FORCE_LOGOUT', reason: 'password-changed' }).reason
    ).toBe('password-changed');
  });

  it('ADMIN_NOTIFICATION с null-полями парсится', () => {
    const msg = AdminNotificationMessageSchema.parse({
      type: 'ADMIN_NOTIFICATION',
      notificationType: 'DEVICE_DISCONNECTED',
      severity: 'WARNING',
      instanceId: null,
      deviceCode: null,
      catalogId: null,
      userId: null,
      message: 'Устройство отключено',
      timestamp: '2026-09-25T10:00:00',
    });
    expect(msg.severity).toBe('WARNING');
  });
});

describe('LiveWsIncomingMessageSchema (discriminated union)', () => {
  it('принимает каждый известный тип', () => {
    const cases: unknown[] = [
      { type: 'ALERT_SNAPSHOT', payload: [] },
      { type: 'NOTIFICATION_SNAPSHOT', payload: [] },
      { type: 'UNITS_STATUS', workshopId: 1, payload: [] },
      validAlert,
      {
        type: 'NOTIFICATION',
        unitId: 'u',
        unitName: 'n',
        creatorId: null,
        active: false,
        timestamp: null,
      },
      { type: 'USER_ASSIGNMENTS', payload: [] },
      { type: 'EMPLOYEE_CHANGED', action: 'UPDATE', payload: null },
      { type: 'WORKSHOP_CHANGED', action: 'UPDATE', payload: null },
      { type: 'ROLE_CHANGED', action: 'UPDATE', payload: null },
      { type: 'UNIT_CHANGED', action: 'UPDATE', payload: null },
      { type: 'DEVICE_CHANGED', action: 'UPDATE', payload: null },
      { type: 'DEVICE_CATALOG_CHANGED', action: 'UPDATE', payload: null },
      { type: 'DEVICE_TYPE_CHANGED', action: 'UPDATE', payload: null },
      { type: 'USER_NOTIFICATION_SETTINGS_CHANGED', action: 'UPDATE', payload: null },
      { type: 'FORCE_LOGOUT', reason: 'r' },
      {
        type: 'ADMIN_NOTIFICATION',
        notificationType: 't',
        severity: 's',
        instanceId: null,
        deviceCode: null,
        catalogId: null,
        userId: null,
        message: 'm',
        timestamp: '2026-09-25T10:00:00',
      },
    ];
    for (const c of cases) {
      expect(LiveWsIncomingMessageSchema.safeParse(c).success).toBe(true);
    }
  });

  it('неизвестный type отбрасывается', () => {
    expect(
      LiveWsIncomingMessageSchema.safeParse({ type: 'FUTURE_EVENT', payload: {} }).success
    ).toBe(false);
  });
});

// ── /ws/unit/{unitId} ─────────────────────────────────────────────────

describe('LineStatusPayloadSchema', () => {
  it('все поля null парсятся', () => {
    const allNull = Object.fromEntries(
      [
        'lineName',
        'lineState',
        'shortCode',
        'description',
        'ean13',
        'batchNumber',
        'dateProduced',
        'datePacking',
        'dateExpiration',
        'initialCounter',
        'site',
        'itf',
        'capacity',
        'boxCount',
        'packageCount',
        'freeze',
        'region',
        'design',
        'printDM',
        'cameraRead',
        'cameraUnread',
      ].map((k) => [k, null])
    );
    const parsed = LineStatusPayloadSchema.parse(allNull);
    expect(parsed.lineName).toBeNull();
    expect(parsed.batchNumber).toBeNull();
  });

  it('полностью пустой объект парсится (все поля nullish)', () => {
    expect(LineStatusPayloadSchema.parse({})).toEqual({});
  });

  it('число вместо строки — отказ', () => {
    expect(LineStatusPayloadSchema.safeParse({ lineName: 42 }).success).toBe(false);
  });
});

describe('DevicesStatusWsPayloadSchema', () => {
  const valid = {
    printers: [{ deviceName: 'Printer11', st: '1', error: null, batch: '1605' }],
    aggregationCams: [
      { deviceName: 'CamAgregation', read: '10', unread: '2', st: '1', error: null },
    ],
    aggregationBoxCams: [],
    checkerCams: [],
  };

  it('валидный payload парсится', () => {
    expect(DevicesStatusWsPayloadSchema.parse(valid).printers).toHaveLength(1);
  });

  it('disconnected опционален, batch: null допустим у принтера', () => {
    const p = {
      ...valid,
      printers: [{ deviceName: 'Printer12', st: null, error: '1', batch: null }],
    };
    const parsed = DevicesStatusWsPayloadSchema.parse(p);
    expect(parsed.printers[0].disconnected).toBeUndefined();
    expect(parsed.printers[0].batch).toBeNull();
  });

  it('отсутствует обязательное nullable-поле batch у принтера — отказ', () => {
    const broken = {
      ...valid,
      printers: [{ deviceName: 'Printer12', st: '1', error: null }],
    };
    expect(DevicesStatusWsPayloadSchema.safeParse(broken).success).toBe(false);
  });

  it('отсутствует read у камеры — отказ (nullable ≠ optional)', () => {
    const broken = {
      ...valid,
      aggregationCams: [{ deviceName: 'Cam1', unread: '1', st: '1', error: null }],
    };
    expect(DevicesStatusWsPayloadSchema.safeParse(broken).success).toBe(false);
  });
});

describe('Queue-схемы', () => {
  it('валидная очередь парсится', () => {
    const item = {
      position: 1,
      shortCode: 'SC-1',
      batch: '1605',
      dateProduced: '25.09.2026',
    };
    expect(QueueItemSchema.parse(item).position).toBe(1);
    expect(QueuePayloadSchema.parse({ items: [item] }).items).toHaveLength(1);
  });

  it('position как строка — отказ', () => {
    expect(
      QueueItemSchema.safeParse({
        position: '1',
        shortCode: 'SC-1',
        batch: '1605',
        dateProduced: '25.09.2026',
      }).success
    ).toBe(false);
  });
});

describe('DeviceErrorSchema / LogEntrySchema', () => {
  const base = {
    objectName: 'Printer11',
    propertyDesc: 'Error',
    value: '1',
  };

  it('description и occurredAt опциональны: без них парсится', () => {
    expect(DeviceErrorSchema.parse(base)).toEqual(base);
  });

  it('occurredAt: null парсится, ISO-строка сохраняется', () => {
    expect(DeviceErrorSchema.parse({ ...base, occurredAt: null }).occurredAt).toBeNull();
    expect(DeviceErrorSchema.parse({ ...base, occurredAt: '2026-09-25T10:00:00' }).occurredAt).toBe(
      '2026-09-25T10:00:00'
    );
  });

  it('value как число — отказ', () => {
    expect(DeviceErrorSchema.safeParse({ ...base, value: 1 }).success).toBe(false);
  });

  it('LogEntry парсится', () => {
    expect(
      LogEntrySchema.parse({
        time: '10:00:00',
        ackTime: '10:01:00',
        group: 'Line',
        description: 'Сброс',
      }).group
    ).toBe('Line');
  });
});

describe('UnitWsMessageSchema (discriminated union)', () => {
  it('принимает все четыре типа', () => {
    expect(UnitWsMessageSchema.safeParse({ type: 'LINE_STATUS', payload: {} }).success).toBe(true);
    expect(
      UnitWsMessageSchema.safeParse({
        type: 'DEVICES_STATUS',
        payload: { printers: [], aggregationCams: [], aggregationBoxCams: [], checkerCams: [] },
      }).success
    ).toBe(true);
    expect(UnitWsMessageSchema.safeParse({ type: 'QUEUE', payload: { items: [] } }).success).toBe(
      true
    );
    expect(UnitWsMessageSchema.safeParse({ type: 'ERRORS', payload: {} }).success).toBe(true);
  });

  it('ERRORS payload допускает отсутствие обоих полей', () => {
    const parsed = UnitWsMessageSchema.parse({ type: 'ERRORS', payload: {} });
    if (parsed.type === 'ERRORS') {
      expect(parsed.payload.deviceErrors).toBeUndefined();
    }
  });

  it('неизвестный type — отказ', () => {
    expect(UnitWsMessageSchema.safeParse({ type: 'CAMERA_FRAME', payload: {} }).success).toBe(
      false
    );
  });
});

describe('AlertSnapshotMessageSchema', () => {
  it('снапшот с одним валидным алёртом парсится', () => {
    expect(
      AlertSnapshotMessageSchema.parse({ type: 'ALERT_SNAPSHOT', payload: [validAlert] }).payload
    ).toHaveLength(1);
  });
});
