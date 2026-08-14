-- Нагрузочное тестирование (эпик #51, НТ-1): 500 тестовых пользователей.
--
-- Применять ПОСЛЕ scripts/seed_notifications.sql (нужны roles/units).
-- Идемпотентно: повторный прогон ничего не дублирует.
--
-- Пользователи:
--   code      = '20001'..'20500' (5 цифр — валидный формат worker code)
--   password  = 'password' (общий BCrypt-хэш, как у dev-пользователей)
--   role      = Master (role_id = 1)
--   password_temporary = false — иначе TemporaryPasswordFilter заблокирует API
--
-- Поддержка make-таргета: make load-db-seed

BEGIN;

INSERT INTO users (role_id, code, password, full_name, is_active, password_temporary)
SELECT 1,
       (20000 + g)::text,
       '$2a$10$Vl9uNi2cKwgVHNiZfQfTXe.YhvzBL0dEwNhvMxiVXPQYhsuIF8bOC',
       'Load User ' || g,
       true,
       false
FROM generate_series(1, 500) AS g
ON CONFLICT (code) DO NOTHING;

-- Назначение на автоматы: по одному автомату на пользователя, равномерно по всем 14.
INSERT INTO user_unit_assignments (user_id, unit_id, assigned_at, is_active)
SELECT u.user_id,
       u2.unit_id,
       NOW(),
       true
FROM users u
         JOIN LATERAL (
    SELECT unit_id
    FROM units
    ORDER BY unit_id
    OFFSET ((u.code::int - 20001) % GREATEST((SELECT COUNT(*) FROM units), 1))
    LIMIT 1
    ) u2 ON true
WHERE u.code BETWEEN '20001' AND '20500'
  AND NOT EXISTS (
    SELECT 1
    FROM user_unit_assignments uua
    WHERE uua.user_id = u.user_id
      AND uua.unit_id = u2.unit_id
);

-- Настройки уведомлений по всем автоматам (как в seed_notifications.sql).
INSERT INTO user_notification_settings
    (user_id, unit_id, incident_notifications_enabled, android_call_notifications_enabled, is_active, updated_at)
SELECT u.user_id,
       un.unit_id,
       true,
       true,
       true,
       NOW()
FROM users u
         CROSS JOIN units un
WHERE u.code BETWEEN '20001' AND '20500'
  AND NOT EXISTS (
    SELECT 1
    FROM user_notification_settings uns
    WHERE uns.user_id = u.user_id
      AND uns.unit_id = un.unit_id
);

SELECT setval(pg_get_serial_sequence('users', 'user_id'), (SELECT COALESCE(MAX(user_id), 1) FROM users));
SELECT setval(pg_get_serial_sequence('user_unit_assignments', 'assignment_id'),
              (SELECT COALESCE(MAX(assignment_id), 1) FROM user_unit_assignments));
SELECT setval(pg_get_serial_sequence('user_notification_settings', 'setting_id'),
              (SELECT COALESCE(MAX(setting_id), 1) FROM user_notification_settings));

COMMIT;
