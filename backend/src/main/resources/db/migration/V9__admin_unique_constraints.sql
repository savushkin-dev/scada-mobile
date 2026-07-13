-- Уникальные ограничения для справочников админ-панели
-- и переименование display_name -> name в device_catalog.

ALTER TABLE device_catalog
    RENAME COLUMN display_name TO name;

ALTER TABLE roles
    ADD CONSTRAINT uc_roles_name UNIQUE (name);

ALTER TABLE workshops
    ADD CONSTRAINT uc_workshops_name UNIQUE (name);

ALTER TABLE device_types
    ADD CONSTRAINT uc_device_types_name UNIQUE (name);

ALTER TABLE device_catalog
    ADD CONSTRAINT uc_device_catalog_name UNIQUE (name);

ALTER TABLE units
    ADD CONSTRAINT uc_units_name_printsrv UNIQUE (name, printsrv_instance_id);
