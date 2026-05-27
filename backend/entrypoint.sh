#!/bin/sh
set -e

# Если контейнер стартовал от root (UID 0) — запускаем java напрямую
if [ "$(id -u)" = "0" ]; then
    echo "Запуск от root (UID 0). Пропускаю переключение пользователя..."
    exec java -jar /app/app.jar
fi

# Иначе — мы создали appuser в Dockerfile, переключаемся на него
USER_ID=$(id -u appuser)
GROUP_ID=$(id -g appuser)

echo "Настраиваю права для юзера $USER_ID:$GROUP_ID..."
chown -R "$USER_ID:$GROUP_ID" /app/logs

echo "Запускаю приложение от юзера appuser..."
exec su-exec appuser java -jar /app/app.jar