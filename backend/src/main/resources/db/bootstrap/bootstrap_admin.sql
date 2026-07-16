-- Bootstrap-скрипт для создания начального администратора.
-- Применяется бэкендом автоматически при первом запуске, если в БД отсутствует
-- пользователь с ролью ADMIN. Перед выполнением бэкенд подставляет сгенерированные
-- значения вместо плейсхолдеров ${ADMIN_CODE} и ${PASSWORD_HASH}.
--
-- ВНИМАНИЕ: пароль является временным — администратор обязан сменить его
-- при первом входе в систему.

INSERT INTO roles (name) VALUES ('ADMIN') ON CONFLICT (name) DO NOTHING;

INSERT INTO users (role_id, code, password, full_name, is_active, password_temporary)
SELECT r.role_id, '${ADMIN_CODE}', '${PASSWORD_HASH}', 'System Administrator', true, true
FROM roles r
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM users u
    JOIN roles rr ON u.role_id = rr.role_id
    WHERE rr.name = 'ADMIN'
  );
