ALTER TABLE users
    ADD code VARCHAR(10);

UPDATE users
SET code = 'USR-' || LPAD(user_id::text, 6, '0')
WHERE code IS NULL;

ALTER TABLE users
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uc_users_code UNIQUE (code);
