-- Возвращаем колонки printsrv_host и printsrv_port в таблицу units.
-- Хосты и порты PrintSrv-инстансов теперь хранятся в БД и редактируются через админ-панель.

ALTER TABLE units
    ADD COLUMN printsrv_host VARCHAR(255),
    ADD COLUMN printsrv_port INTEGER;
