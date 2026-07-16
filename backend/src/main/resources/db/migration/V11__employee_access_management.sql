-- Управление доступом сотрудников: временные пароли и принудительная смена пароля.
ALTER TABLE users
    ADD COLUMN password_temporary BOOLEAN NOT NULL DEFAULT FALSE;

-- У существующих пользователей пароль не является временным.
UPDATE users SET password_temporary = FALSE;
