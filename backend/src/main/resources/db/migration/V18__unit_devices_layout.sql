-- Per-unit раскладка устройств: имена, группы, порядок, счётчики, scada-префиксы.
-- Основание: PRINTSRV_UI_MAP.md (паритет со старой SCADA).
-- Идемпотентно: безопасно применять повторно.

ALTER TABLE unit_devices ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE unit_devices ADD COLUMN IF NOT EXISTS group_label VARCHAR(255);
ALTER TABLE unit_devices ADD COLUMN IF NOT EXISTS display_order INT NOT NULL DEFAULT 0;
ALTER TABLE unit_devices ADD COLUMN IF NOT EXISTS show_counters BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE unit_devices ADD COLUMN IF NOT EXISTS scada_prefix VARCHAR(64);

-- Обратная заливка дефолтов name-based правилами (подтверждены реальными логами PrintSrv):
--   CamAgregation[N]    → Dev04(2N−1)  (CamAgregation/CamAgregation1 → Dev041, 2 → Dev043)
--   CamAgregationBox[N] → Dev04(2N)    (CamAgregationBox/Box1 → Dev042, Box2 → Dev044)
--   CamEanCheckerN      → Dev07N
--   PrinterNN           → LineDev0NN   (Printer11 → LineDev011, Printer2 → LineDev02)
-- Правила по имени кода, а не по типу каталога: часть установок имеет
-- misclassified типы (например CamAgregation как checker_cam).
-- show_counters=true только камерам агрегации (старая SCADA показывает счётчики
-- только первой камере каждого потока); display_order = текущий порядок строк.
WITH ranked AS (
    SELECT ud.device_id,
           dc.code AS device_code,
           ROW_NUMBER() OVER (PARTITION BY ud.unit_id ORDER BY dc.code, ud.device_id) - 1 AS ord
    FROM unit_devices ud
             JOIN device_catalog dc ON dc.catalog_id = ud.catalog_id
),
parsed AS (
    SELECT device_id, device_code, ord,
           coalesce(substring(device_code from '([0-9]+)$')::int, 1) AS n
    FROM ranked
)
UPDATE unit_devices ud
SET display_order = p.ord,
    show_counters = p.device_code ~ '^CamAgregation[0-9]*$',
    scada_prefix  = CASE
        WHEN p.device_code ~ '^CamAgregationBox[0-9]*$'
            THEN 'Dev' || lpad((42 + (p.n - 1) * 2)::text, 3, '0')
        WHEN p.device_code ~ '^CamAgregation[0-9]*$'
            THEN 'Dev' || lpad((41 + (p.n - 1) * 2)::text, 3, '0')
        WHEN p.device_code ~ '^CamEanChecker[0-9]+$'
            THEN 'Dev' || lpad((70 + p.n)::text, 3, '0')
        WHEN p.device_code ~ '^Printer[0-9]+$'
            THEN 'LineDev0' || substring(p.device_code from 'Printer([0-9]+)')
        ELSE NULL END
FROM parsed p
WHERE p.device_id = ud.device_id;
