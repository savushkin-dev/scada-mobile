#!/usr/bin/env python3
"""
Генератор учётных данных начального администратора и SQL-скриптов.

Запуск:
    python scripts/generate_admin_credentials.py

Результат:
    - обновляет scripts/bootstrap_admin.sql
    - обновляет scripts/reset_admin_password.sql
    - выводит в stdout сгенерированные код, пароль и хеш

Используемый пароль — временный; при первом входе администратор должен
сменить его через /change-password.
"""

import secrets
import string
import re
import bcrypt


LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ"
DIGITS = "23456789"
ALPHABET = LETTERS + DIGITS
CODE_LENGTH = 8
PASSWORD_LENGTH = 8


def _has_triple_repeat(value: str) -> bool:
    return any(value[i] == value[i - 1] == value[i - 2] for i in range(2, len(value)))


def generate_code() -> str:
    while True:
        value = "".join(secrets.choice(ALPHABET) for _ in range(CODE_LENGTH))
        if any(c in value for c in LETTERS) and any(c in value for c in DIGITS) and not _has_triple_repeat(value):
            return value


def generate_temporary_password() -> str:
    while True:
        value = "".join(secrets.choice(ALPHABET) for _ in range(PASSWORD_LENGTH))
        if any(c in value for c in LETTERS) and any(c in value for c in DIGITS) and not _has_triple_repeat(value):
            return value


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("utf-8")


def bootstrap_sql(code: str, password_hash: str) -> str:
    return f"""-- Bootstrap-скрипт для создания начального администратора.
--
-- ВНИМАНИЕ: этот файл сгенерирован автоматически. Применяйте его вручную
-- только если бэкенд не может сам создать администратора при первом запуске.
-- Пароль является временным — администратор обязан сменить его при первом входе.
--
-- Generated code:     {code}
-- Generated password: <скрыт, см. логи приложения или stdout при генерации>

INSERT INTO roles (name) VALUES ('ADMIN') ON CONFLICT (name) DO NOTHING;

INSERT INTO users (role_id, code, password, full_name, is_active, password_temporary)
SELECT r.role_id, '{code}', '{password_hash}', 'System Administrator', true, true
FROM roles r
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM users u
    JOIN roles rr ON u.role_id = rr.role_id
    WHERE rr.name = 'ADMIN'
  );
"""


def reset_sql(code: str, password_hash: str) -> str:
    return f"""-- Скрипт ручного сброса пароля администратору.
--
-- ВНИМАНИЕ: замените значения переменных ниже при необходимости
-- и выполните скрипт от имени владельца БД.
-- Пароль является временным — администратор обязан сменить его при следующем входе.
--
-- Generated for admin code: {code}

\\set admin_code '{code}'
\\set new_password_hash '{password_hash}'

UPDATE users
SET password = :'new_password_hash',
    password_temporary = true
WHERE code = :'admin_code'
  AND role_id = (SELECT role_id FROM roles WHERE name = 'ADMIN');
"""


def main() -> None:
    code = generate_code()
    password = generate_temporary_password()
    password_hash = hash_password(password)

    print(f"Admin code:     {code}")
    print(f"Admin password: {password}")
    print(f"Password hash:  {password_hash}")

    with open("scripts/bootstrap_admin.sql", "w", encoding="utf-8", newline="\n") as f:
        f.write(bootstrap_sql(code, password_hash))

    with open("scripts/reset_admin_password.sql", "w", encoding="utf-8", newline="\n") as f:
        f.write(reset_sql(code, password_hash))

    print("\nUpdated scripts/bootstrap_admin.sql")
    print("Updated scripts/reset_admin_password.sql")


if __name__ == "__main__":
    main()
