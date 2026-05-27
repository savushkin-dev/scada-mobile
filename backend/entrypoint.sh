#!/bin/sh
set -e

# UID/GID appuser (они совпадают с хостом, т.к. создали с нужными ID)
USER_ID=$(id -u appuser)
GROUP_ID=$(id -g appuser)

echo "Настраиваю права для юзера $USER_ID:$GROUP_ID..."

# Делаем appuser владельцем папки логов
chown -R "$USER_ID:$GROUP_ID" /app/logs

# Запускаем приложение от appuser
echo "Запускаю приложение от юзера appuser..."
exec su-exec appuser java -jar /app/app.jar