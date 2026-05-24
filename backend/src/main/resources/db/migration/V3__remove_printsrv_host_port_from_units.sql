-- Удаляем колонки printsrv_host и printsrv_port из таблицы units.
-- Хосты и порты PrintSrv-инстансов теперь задаются через переменные окружения
-- (SCADA_MOBILE_PRINTSRV_{INSTANCE_ID}_HOST / _PORT) и читаются через
-- PrintSrvHostProperties при старте приложения.

ALTER TABLE units
    DROP COLUMN IF EXISTS printsrv_host,
    DROP COLUMN IF EXISTS printsrv_port;
