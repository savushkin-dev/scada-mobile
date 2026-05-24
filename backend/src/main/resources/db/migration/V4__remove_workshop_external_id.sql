-- Удаляем колонку external_id из таблицы workshops.
-- Теперь используется внутренний ID (workshop_id) для всех ссылок.

ALTER TABLE workshops
    DROP COLUMN IF EXISTS external_id;
