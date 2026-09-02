-- История уведомлений: раньше на аппарат хранилась одна строка (уникальный unit_id),
-- перезаписываемая при каждом toggle. Для sent-history / executor-history нужна
-- полная история — каждая активация создаёт новую строку.
ALTER TABLE production_notifications
    DROP CONSTRAINT uc_production_notifications_unit;

-- Инвариант «не более одного активного уведомления на аппарат» сохраняется
-- частичным уникальным индексом только по активным строкам.
CREATE UNIQUE INDEX ux_production_notifications_active_unit
    ON production_notifications(unit_id) WHERE is_active;
