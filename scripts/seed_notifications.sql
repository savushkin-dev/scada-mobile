BEGIN;

INSERT INTO roles (role_id, name)
VALUES (1, 'Master')
ON CONFLICT (role_id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO users (user_id, role_id, code, password, full_name, is_active)
VALUES (1, 1, 'USR-000001', 'PWD-000001', 'Test User', true)
ON CONFLICT (user_id) DO UPDATE SET role_id = EXCLUDED.role_id,
                                   code = EXCLUDED.code,
                                   password = EXCLUDED.password,
                                   full_name = EXCLUDED.full_name,
                                   is_active = EXCLUDED.is_active;

INSERT INTO workshops (workshop_id, name, is_active)
VALUES (1, 'Цех десертов', true),
       (2, 'Цех розлива', true)
ON CONFLICT (workshop_id) DO UPDATE SET name = EXCLUDED.name,
                                      is_active = EXCLUDED.is_active;

INSERT INTO units (unit_id, workshop_id, name, is_active, printsrv_instance_id)
VALUES
    (1, 1, 'Trepko №1', true, 'trepko1'),
    (2, 1, 'Trepko №2', true, 'trepko2'),
    (3, 1, 'Hassia №1', true, 'hassia1'),
    (4, 1, 'Hassia №2', true, 'hassia2'),
    (5, 1, 'Hassia №4', true, 'hassia4'),
    (6, 1, 'Hassia №5', true, 'hassia5'),
    (7, 1, 'Hassia №6', true, 'hassia6'),
    (8, 1, 'Hassia №3', true, 'hassia3'),
    (9, 1, 'Bosch', true, 'bosch'),
    (10, 1, 'Grunwald №5', true, 'grunwald5'),
    (11, 1, 'Grunwald №8', true, 'grunwald8'),
    (12, 2, 'Grunwald №1', true, 'grunwald1'),
    (13, 2, 'Grunwald №2', true, 'grunwald2'),
    (14, 2, 'Grunwald №11', true, 'grunwald11')
ON CONFLICT (unit_id) DO UPDATE SET workshop_id = EXCLUDED.workshop_id,
                                   name = EXCLUDED.name,
                                   is_active = EXCLUDED.is_active,
                                   printsrv_instance_id = EXCLUDED.printsrv_instance_id;

INSERT INTO device_types (code, name)
VALUES ('printer', 'Принтер'),
       ('aggregation_cam', 'Камера агрегации'),
       ('aggregation_box_cam', 'Камера агрегации на коробе'),
       ('checker_cam', 'Камера проверки')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

WITH device_rows (printsrv_instance_id, type_code, device_code, display_name) AS (
    VALUES
        ('trepko1', 'aggregation_cam', 'CamBatch', 'CamBatch'),
        ('trepko1', 'aggregation_box_cam', 'CamPacker', 'CamPacker'),
        ('trepko1', 'aggregation_box_cam', 'CamPackerBox', 'CamPackerBox'),
        ('trepko1', 'checker_cam', 'CamChecker', 'CamChecker'),
        ('trepko2', 'aggregation_cam', 'CamBatch', 'CamBatch'),
        ('trepko2', 'aggregation_box_cam', 'CamPacker', 'CamPacker'),
        ('trepko2', 'aggregation_box_cam', 'CamPackerBox', 'CamPackerBox'),
        ('trepko2', 'checker_cam', 'CamChecker', 'CamChecker'),
        ('hassia4', 'printer', 'Printer11', 'Printer11'),
        ('hassia4', 'printer', 'Printer12', 'Printer12'),
        ('hassia4', 'printer', 'Printer2', 'Printer2'),
        ('hassia5', 'printer', 'Printer11', 'Printer11'),
        ('hassia5', 'printer', 'Printer12', 'Printer12'),
        ('hassia6', 'printer', 'Printer11', 'Printer11'),
        ('hassia6', 'printer', 'Printer12', 'Printer12'),
        ('hassia3', 'printer', 'Printer11', 'Printer11'),
        ('hassia3', 'printer', 'Printer12', 'Printer12'),
        ('bosch', 'printer', 'Printer11', 'Printer11'),
        ('bosch', 'printer', 'Printer12', 'Printer12'),
        ('grunwald5', 'printer', 'Printer11', 'Printer11'),
        ('grunwald5', 'printer', 'Printer12', 'Printer12'),
        ('grunwald8', 'printer', 'Printer11', 'Printer11'),
        ('grunwald8', 'printer', 'Printer12', 'Printer12'),
        ('grunwald1', 'printer', 'Printer11', 'Printer11'),
        ('grunwald1', 'printer', 'Printer12', 'Printer12'),
        ('grunwald1', 'checker_cam', 'CamChecker', 'CamChecker'),
        ('grunwald1', 'checker_cam', 'CamEanChecker1', 'CamEanChecker1'),
        ('grunwald1', 'checker_cam', 'CamEanChecker2', 'CamEanChecker2'),
        ('grunwald1', 'checker_cam', 'CamEanChecker3', 'CamEanChecker3'),
        ('grunwald1', 'checker_cam', 'CamEanChecker4', 'CamEanChecker4'),
        ('grunwald2', 'printer', 'Printer11', 'Printer11'),
        ('grunwald2', 'printer', 'Printer12', 'Printer12'),
        ('grunwald2', 'checker_cam', 'CamChecker', 'CamChecker'),
        ('grunwald2', 'checker_cam', 'CamEanChecker1', 'CamEanChecker1'),
        ('grunwald2', 'checker_cam', 'CamEanChecker2', 'CamEanChecker2'),
        ('grunwald2', 'checker_cam', 'CamEanChecker3', 'CamEanChecker3'),
        ('grunwald2', 'checker_cam', 'CamEanChecker4', 'CamEanChecker4'),
        ('grunwald11', 'printer', 'Printer11', 'Printer11'),
        ('grunwald11', 'printer', 'Printer12', 'Printer12'),
        ('grunwald11', 'printer', 'Printer13', 'Printer13'),
        ('grunwald11', 'printer', 'Printer14', 'Printer14'),
        ('grunwald11', 'aggregation_cam', 'CamAgregation1', 'CamAgregation1'),
        ('grunwald11', 'aggregation_cam', 'CamAgregation2', 'CamAgregation2'),
        ('grunwald11', 'aggregation_box_cam', 'CamAgregationBox1', 'CamAgregationBox1'),
        ('grunwald11', 'aggregation_box_cam', 'CamAgregationBox2', 'CamAgregationBox2'),
        ('grunwald11', 'checker_cam', 'CamChecker1', 'CamChecker1'),
        ('grunwald11', 'checker_cam', 'CamChecker2', 'CamChecker2'),
        ('grunwald11', 'checker_cam', 'CamEanChecker1', 'CamEanChecker1'),
        ('grunwald11', 'checker_cam', 'CamEanChecker2', 'CamEanChecker2'),
        ('grunwald11', 'checker_cam', 'CamEanChecker3', 'CamEanChecker3'),
        ('grunwald11', 'checker_cam', 'CamEanChecker4', 'CamEanChecker4')
)
INSERT INTO unit_devices (unit_id, type_id, code, display_name)
SELECT u.unit_id,
       dt.type_id,
       d.device_code,
       d.display_name
FROM device_rows d
JOIN units u ON u.printsrv_instance_id = d.printsrv_instance_id
JOIN device_types dt ON dt.code = d.type_code
WHERE NOT EXISTS (
    SELECT 1
    FROM unit_devices ud
    WHERE ud.unit_id = u.unit_id
      AND ud.type_id = dt.type_id
      AND ud.code = d.device_code
);

DELETE FROM user_unit_assignments WHERE user_id = 1;
DELETE FROM user_notification_settings WHERE user_id = 1;

INSERT INTO user_unit_assignments (user_id, unit_id, assigned_at, is_active)
VALUES (1, 1, NOW(), true),
       (1, 3, NOW(), true);

INSERT INTO user_notification_settings
    (user_id, unit_id, incident_notifications_enabled, android_call_notifications_enabled, is_active, updated_at)
SELECT 1,
       u.unit_id,
       true,
       true,
       true,
       NOW()
FROM units u
WHERE NOT EXISTS (
    SELECT 1
    FROM user_notification_settings uns
    WHERE uns.user_id = 1
      AND uns.unit_id = u.unit_id
);

SELECT setval(pg_get_serial_sequence('roles', 'role_id'), (SELECT COALESCE(MAX(role_id), 1) FROM roles));
SELECT setval(pg_get_serial_sequence('users', 'user_id'), (SELECT COALESCE(MAX(user_id), 1) FROM users));
SELECT setval(pg_get_serial_sequence('workshops', 'workshop_id'), (SELECT COALESCE(MAX(workshop_id), 1) FROM workshops));
SELECT setval(pg_get_serial_sequence('units', 'unit_id'), (SELECT COALESCE(MAX(unit_id), 1) FROM units));
SELECT setval(pg_get_serial_sequence('device_types', 'type_id'), (SELECT COALESCE(MAX(type_id), 1) FROM device_types));
SELECT setval(pg_get_serial_sequence('unit_devices', 'device_id'), (SELECT COALESCE(MAX(device_id), 1) FROM unit_devices));
SELECT setval(pg_get_serial_sequence('user_unit_assignments', 'assignment_id'),
              (SELECT COALESCE(MAX(assignment_id), 1) FROM user_unit_assignments));
SELECT setval(pg_get_serial_sequence('user_notification_settings', 'setting_id'),
              (SELECT COALESCE(MAX(setting_id), 1) FROM user_notification_settings));

COMMIT;
