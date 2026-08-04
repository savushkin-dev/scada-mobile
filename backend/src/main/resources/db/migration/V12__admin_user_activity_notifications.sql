-- V12__admin_user_activity_notifications.sql
-- Уведомления администратора о событиях сотрудников (смена пароля, длительное бездействие):
--   * admin_notifications.instance_id становится необязательным (события сотрудников не привязаны к автомату);
--   * admin_notifications.user_id — сотрудник, которого касается событие;
--   * users.last_activity_at — метка последней активности сотрудника (логин, refresh, смена пароля).

ALTER TABLE admin_notifications
    ALTER COLUMN instance_id DROP NOT NULL;

ALTER TABLE admin_notifications
    ADD COLUMN user_id BIGINT NULL REFERENCES users (user_id);

CREATE INDEX idx_admin_notifications_user ON admin_notifications (user_id, created_at DESC);

ALTER TABLE users
    ADD COLUMN last_activity_at TIMESTAMP NULL;
