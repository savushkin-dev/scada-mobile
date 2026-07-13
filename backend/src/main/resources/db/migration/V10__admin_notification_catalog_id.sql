-- V10__admin_notification_catalog_id.sql
-- Связь системного уведомления администратора с записью в справочнике устройств.
-- Нужна для перехода на редактирование устройства из уведомления "Warning".

ALTER TABLE admin_notifications
    ADD COLUMN catalog_id BIGINT NULL,
    ADD CONSTRAINT fk_admin_notifications_on_catalog
        FOREIGN KEY (catalog_id) REFERENCES device_catalog (catalog_id);

CREATE INDEX idx_admin_notifications_catalog ON admin_notifications (catalog_id);
