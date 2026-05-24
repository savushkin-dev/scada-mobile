ALTER TABLE units
    ADD COLUMN printsrv_host VARCHAR(255),
    ADD COLUMN printsrv_port INTEGER;

ALTER TABLE workshops
    ADD COLUMN external_id VARCHAR(255);

ALTER TABLE workshops
    ADD CONSTRAINT uc_workshops_external_id UNIQUE (external_id);
