-- Скрытие устройства с экрана без удаления связи.
-- Удалённая связь возвращается auto-discovery, если устройство есть в runtime —
-- поэтому «не показывать» реализовано флагом, а не удалением.

ALTER TABLE unit_devices ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;
