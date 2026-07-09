-- V7__device_catalog_active_and_nullable_type.sql
-- Добавляет is_active и делает type_id nullable для авто-обнаружения устройств

-- 1. Делаем type_id nullable (нужно для авто-обнаружения без типа)
ALTER TABLE device_catalog
    ALTER COLUMN type_id DROP NOT NULL;

-- 2. Добавляем is_active
ALTER TABLE device_catalog
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- 3. Создаём индекс для фильтрации active-устройств
CREATE INDEX idx_device_catalog_active ON device_catalog(is_active);

-- 4. Уникальность по code: в справочнике не может быть двух устройств с одинаковым runtime-кодом
-- Проверяем наличие дублей перед добавлением constraint
DO $$
DECLARE
    duplicate_count INT;
BEGIN
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT code, COUNT(*) as cnt
        FROM device_catalog
        GROUP BY code
        HAVING COUNT(*) > 1
    ) t;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'Found % duplicate code(s) in device_catalog. Resolve manually before adding UNIQUE constraint.', duplicate_count;
    END IF;
END $$;

ALTER TABLE device_catalog
    ADD CONSTRAINT uc_device_catalog_code UNIQUE (code);
