-- Миграция: переход с plaintext паролей на bcrypt хэши.
--
-- 1. Убираем уникальный констрейнт на password (был антипаттерн).
-- 2. Увеличиваем размер колонки с VARCHAR(10) до VARCHAR(60) — bcrypt hash = 60 символов.
--
-- Примечание: существующие plaintext пароли НЕ мигрируются автоматически.
-- Администратор должен сбросить пароли пользователей через отдельный скрипт
-- или интерфейс. Приложение отклонит plaintext пароли при аутентификации
-- (bcrypt не совпадёт).

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS uc_users_password;

ALTER TABLE users
    ALTER COLUMN password TYPE VARCHAR(60);
