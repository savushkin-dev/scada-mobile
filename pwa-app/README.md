# SCADA Mobile — PWA (self-contained)

Коротко: это статический Progressive Web App, собранный в папке `pwa-app/`. Приложение работает офлайн с помощью Service Worker, имеет веб-манифест и иконки для установки на домашний экран. Этот README описывает только содержимое и работу папки `pwa-app`.

## Что в папке

```
/pwa-app
├─ index.html              # Входная страница
├─ manifest.webmanifest    # Web App Manifest (start_url, scope и т.д.)
├─ service-worker.js       # Service Worker (offline cache)
├─ README.md               # Этот файл
├─ assets/                 # Иконки, скриншоты и прочие ассеты
├─ css/styles.css          # Стили
└─ js/app.js               # Небольшая логика/регистрация SW
```

## Быстрый старт (локально)

1) Перейдите в папку `pwa-app`:

```bash
cd pwa-app
```

2) Если в папке есть `package.json` (для dev-сервера), можно установить зависимости и запустить dev-сервер:

```bash
# только при наличии package.json
npm install
npm run dev
```

3) Альтернативы (быстрый статический сервер):

```bash
# Python
python -m http.server 8000

# npx http-server
npx http-server -p 8000
```

Откройте в браузере: `http://localhost:8000/`.

Примечание: менеджер пакетов и dev-сервер — опциональны. В большинстве случаев `pwa-app/` — это статическая папка, готовая к публикации.

## Рекомендации для production (GitHub Pages / Netlify)

- Если вы публикуете сайт на GitHub Pages под путём `/scada-mobile/`, убедитесь, что в `manifest.webmanifest` указаны корректные поля:

```json
"start_url": "/scada-mobile/",
"scope": "/scada-mobile/"
```

Это гарантирует, что установленное приложение всегда стартует с корня приложения и не приводит к 404 при установке с вложенной страницы.

- Для Netlify: если у вас нет сборки, просто укажите `pwa-app/` как `Publish directory`. Если есть сборка (например, `npm run build`), публикуйте выходную папку (`pwa-app/dist` или `pwa-app/build`).

## Установка PWA

- Откройте сайт в Chrome на Android → меню → "Добавить на главный экран".
- На десктопе Chrome/Edge — кнопка установки в адресной строке.

Важно: если пользователь установил приложение с URL, отличного от корня приложения (например `/scada-mobile/some/page`), и `start_url` задан как относительный (`"./"`), при запуске установленного приложения может открываться вложенный путь и приводить к 404. Поэтому для production (GitHub Pages с базовым путём) рекомендуется абсолютный `start_url` и `scope` как указано выше.

## Service Worker и оффлайн

Файл `service-worker.js` реализует простую стратегию cache-first: ключевые ресурсы кэшируются при установке, при запросе сначала возвращается кэш, при его отсутствии — сеть. При каждом обновлении Service Worker происходит обновление кэша.

Если вам нужно принудительно сбросить кэш при разработке, откройте DevTools → Application → Service Workers → Unregister, и очистите Storage → Clear site data.

## Тестирование и отладка

- Chrome DevTools → Application:
    - Проверить `Manifest`
    - Проверить Service Worker и Cache Storage
- Lighthouse (DevTools → Lighthouse) — запуск аудита PWA

## Безопасность и секреты

- В репозитории не должно быть keystore, паролей или приватных ключей (см. корневой `.gitignore`).

## Что делать, если при установке открывается 404

1. Проверьте `start_url` и `scope` в `manifest.webmanifest`.
2. Убедитесь, что все пути в манифесте (иконки, screenshots) доступны по указанным путям.
3. Если сайт размещён в поддиректории (как на GitHub Pages), используйте абсолютные пути, начинающиеся с `/<repo-name>/`.

## Контакты / поддержка

Если нужно — создайте issue в основном репозитории `savushkin-dev/scada-mobile` с описанием проблемы и шагами воспроизведения.

---
Файл описывает только содержимое `pwa-app/` и рекомендации для стабильной работы PWA в production.
