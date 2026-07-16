-- Bootstrap-скрипт для создания начального администратора.
--
-- ВНИМАНИЕ: этот файл сгенерирован автоматически. Применяйте его вручную
-- только если бэкенд не может сам создать администратора при первом запуске.
-- Пароль является временным — администратор обязан сменить его при первом входе.
--
-- Generated code:     934FGQ6Q
-- Generated password: <скрыт, см. логи приложения или stdout при генерации>

INSERT INTO roles (name) VALUES ('ADMIN') ON CONFLICT (name) DO NOTHING;

INSERT INTO users (role_id, code, password, full_name, is_active, password_temporary)
SELECT r.role_id, '934FGQ6Q', '$2b$10$Y5qYCHpVj5So4kX6dmInreMXwLI0QSqhfUQb7NEaBT0thDZHpBOfa', 'System Administrator', true, true
FROM roles r
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM users u
    JOIN roles rr ON u.role_id = rr.role_id
    WHERE rr.name = 'ADMIN'
  );
