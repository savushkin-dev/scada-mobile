-- Скрипт ручного сброса пароля администратору.
--
-- ВНИМАНИЕ: замените значения переменных ниже при необходимости
-- и выполните скрипт от имени владельца БД.
-- Пароль является временным — администратор обязан сменить его при следующем входе.
--
-- Generated for admin code: 934FGQ6Q

\set admin_code '934FGQ6Q'
\set new_password_hash '$2b$10$Y5qYCHpVj5So4kX6dmInreMXwLI0QSqhfUQb7NEaBT0thDZHpBOfa'

UPDATE users
SET password = :'new_password_hash',
    password_temporary = true
WHERE code = :'admin_code'
  AND role_id = (SELECT role_id FROM roles WHERE name = 'ADMIN');
