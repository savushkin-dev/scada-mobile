BEGIN;

INSERT INTO roles (role_id, name)
VALUES (1, 'Master')
ON CONFLICT (role_id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO users (user_id, role_id, full_name, is_active)
VALUES (1, 1, 'Test User', true)
ON CONFLICT (user_id) DO UPDATE SET role_id = EXCLUDED.role_id,
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

DELETE FROM user_unit_assignments WHERE user_id = 1;
DELETE FROM user_notification_settings WHERE user_id = 1;

INSERT INTO user_unit_assignments (user_id, unit_id, assigned_at, is_active)
VALUES (1, 1, NOW(), true),
       (1, 3, NOW(), true);

INSERT INTO user_notification_settings
    (user_id, unit_id, system_sound_enabled, system_vibration_enabled, android_push_enabled, is_active, updated_at)
VALUES
    (1, 1, true, true, true, true, NOW()),
    (1, 3, true, true, true, true, NOW());

SELECT setval(pg_get_serial_sequence('roles', 'role_id'), (SELECT COALESCE(MAX(role_id), 1) FROM roles));
SELECT setval(pg_get_serial_sequence('users', 'user_id'), (SELECT COALESCE(MAX(user_id), 1) FROM users));
SELECT setval(pg_get_serial_sequence('workshops', 'workshop_id'), (SELECT COALESCE(MAX(workshop_id), 1) FROM workshops));
SELECT setval(pg_get_serial_sequence('units', 'unit_id'), (SELECT COALESCE(MAX(unit_id), 1) FROM units));
SELECT setval(pg_get_serial_sequence('user_unit_assignments', 'assignment_id'),
              (SELECT COALESCE(MAX(assignment_id), 1) FROM user_unit_assignments));
SELECT setval(pg_get_serial_sequence('user_notification_settings', 'setting_id'),
              (SELECT COALESCE(MAX(setting_id), 1) FROM user_notification_settings));

COMMIT;
