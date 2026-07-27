-- Миграция: замена старых кодов сотрудников на 5-значные табельные номера.
--
-- Применение:
--   psql -U <user> -d <db> -f scripts/migrate_worker_code_to_5digits.sql
--
-- Логика:
--   - пользователям, у которых code уже состоит из 5 цифр, ничего не меняется;
--   - остальным назначаются уникальные табельные номера из диапазона 10000–99999.

WITH numbered_users AS (
    SELECT
        u.user_id,
        (10000 + ROW_NUMBER() OVER (ORDER BY u.user_id) - 1)::TEXT AS new_code
    FROM users u
    WHERE u.code !~ '^\d{5}$'
)
UPDATE users u
SET code = nu.new_code
FROM numbered_users nu
WHERE u.user_id = nu.user_id;
