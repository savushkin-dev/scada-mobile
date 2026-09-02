ALTER TABLE production_notifications
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN accepted_by VARCHAR(255),
    ADD COLUMN accepted_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN completed_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE production_notifications
SET status = CASE WHEN is_active THEN 'PENDING' ELSE 'COMPLETED' END,
    completed_at = CASE WHEN is_active THEN NULL ELSE deactivated_at END,
    cancelled_at = NULL;

ALTER TABLE production_notifications
    ALTER COLUMN status SET NOT NULL;

CREATE INDEX idx_production_notifications_status
    ON production_notifications(status);
CREATE INDEX idx_production_notifications_accepted_by
    ON production_notifications(accepted_by);
