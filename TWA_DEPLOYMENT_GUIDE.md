# 🚀 Руководство по деплою TWA (Trusted Web Activity)

## 📋 Содержание

1. [Почему не работает TWA локально](#почему-не-работает-twa-локально)
2. [Текущая конфигурация (Development)](#текущая-конфигурация-development)
3. [Подготовка к Production](#подготовка-к-production)
4. [Пошаговая инструкция деплоя](#пошаговая-инструкция-деплоя)
5. [Проверка работы TWA](#проверка-работы-twa)
6. [Troubleshooting](#troubleshooting)

---

## 🔍 Почему не работает TWA локально

### Проблема: Полоска сверху в Android приложении

**Причина:** Digital Asset Links (система верификации доверия между PWA и Android приложением) **НЕ РАБОТАЕТ** с локальными адресами.

### Технические ограничения

1. **Android требует HTTPS** для верификации Digital Asset Links
2. **Локальные адреса не поддерживаются:**
   - ❌ `http://127.0.0.1:8000`
   - ❌ `http://localhost:8000`
   - ❌ `http://192.168.x.x:8000`
3. **Digital Asset Links проверяются только на публичных доменах:**
   - ✅ `https://your-domain.com`
   - ✅ `https://your-app.github.io`
   - ✅ `https://your-app.netlify.app`

### Что происходит сейчас

- Android пытается проверить файл `assetlinks.json` по адресу:

  ```
  http://127.0.0.1:8000/.well-known/assetlinks.json
  ```

- Верификация **не проходит**, потому что:
  1. Это локальный адрес (недоступен для Android Asset Links API)
  2. Используется HTTP вместо HTTPS
  3. Android не может гарантировать, что приложение доверяет этому домену

**Результат:** Chrome показывает полоску сверху (Custom Tabs mode вместо TWA mode).

---

## 🛠️ Текущая конфигурация (Development)

### Для локальной разработки

Текущая конфигурация **специально настроена** для работы БЕЗ Digital Asset Links:

**AndroidManifest.xml:**

- ✅ Убран `android:autoVerify="true"` (не работает локально)
- ✅ URL: `http://127.0.0.1:8000/`
- ✅ Порт: `8000` (совпадает с `npm run dev`)

**MainActivity.kt:**

- ✅ URL: `http://127.0.0.1:8000/`

**Что ожидать:**

- ✅ Приложение запускается
- ✅ PWA отображается в Chrome Custom Tabs
- ⚠️ **Полоска сверху БУДЕТ ВИДНА** (это нормально для локальной разработки)
- ✅ Функциональность работает полностью

### Как запустить локально

```bash
# 1. Запустить PWA сервер
cd pwa-app
npm install
npm run dev

# 2. Запустить Android приложение
cd ../twa-mobile
./gradlew installDebug

# 3. Открыть приложение на устройстве
```

---

## 📦 Подготовка к Production

### Шаг 1: Выбрать платформу для хостинга PWA

Выберите один из вариантов:

#### Вариант А: GitHub Pages (бесплатно, HTTPS автоматически)

```bash
# 1. Создать репозиторий на GitHub
# 2. Загрузить содержимое pwa-app
# 3. Включить GitHub Pages в настройках репозитория

# Ваш URL будет:
# https://<username>.github.io/<repo-name>/
```

#### Вариант Б: Netlify (бесплатно, автоматический деплой)

```bash
# 1. Зарегистрироваться на netlify.com
# 2. Подключить GitHub репозиторий
# 3. Указать папку сборки: pwa-app
# 4. Deploy!

# Ваш URL будет:
# https://<your-app>.netlify.app/
```

#### Вариант В: Vercel (бесплатно, быстрый)

```bash
# 1. Зарегистрироваться на vercel.com
# 2. Импортировать репозиторий
# 3. Root Directory: pwa-app
# 4. Deploy!

# Ваш URL будет:
# https://<your-app>.vercel.app/
```

#### Вариант Г: Собственный сервер с HTTPS

```bash
# Требования:
# - Доменное имя (example.com)
# - SSL сертификат (Let's Encrypt бесплатно)
# - Веб-сервер (nginx, Apache)
```

---

## 🚀 Пошаговая инструкция деплоя

### Шаг 1: Деплой PWA на HTTPS

Выполните деплой `pwa-app` на выбранную платформу.

**Пример для GitHub Pages:**

```bash
cd pwa-app

# Создать branch gh-pages
git checkout -b gh-pages

# Добавить все файлы
git add .
git commit -m "Deploy PWA to GitHub Pages"

# Отправить на GitHub
git push origin gh-pages

# Включить GitHub Pages в настройках репозитория:
# Settings -> Pages -> Source: gh-pages branch -> Save
```

**Получите URL вида:**

```
https://savushkin-dev.github.io/scada-mobile/
```

### Шаг 2: Проверить доступность assetlinks.json

Убедитесь, что файл доступен по адресу:

```
https://ваш-домен.com/.well-known/assetlinks.json
```

**Тест в браузере:**

```
https://savushkin-dev.github.io/scada-mobile/.well-known/assetlinks.json
```

Должен вернуть JSON с вашим SHA-256 отпечатком.

### Шаг 3: Получить Production SHA-256 (если нужно)

Если вы планируете подписывать Release версию приложения своим ключом:

```bash
cd twa-mobile

# Создать keystore (если нет)
keytool -genkey -v -keystore release-key.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000

# Получить SHA-256 для release
./gradlew signingReport
```

**Обновите `assetlinks.json` с новым SHA-256 для release:**

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.savushkin.scada.mobile",
      "sha256_cert_fingerprints": [
        "A5:42:03:8F:8F:29:DD:B8:C3:BF:CA:3C:9F:21:1D:9B:F4:82:13:18:A8:94:DB:EE:56:3F:25:D1:07:3E:2D:E4",
        "YOUR_RELEASE_SHA256_HERE"
      ]
    }
  }
]
```

### Шаг 4: Обновить AndroidManifest.xml

**Откройте:** `twa-mobile/app/src/main/AndroidManifest.xml`

**Найдите и замените:**

```xml
<!-- Было (DEV): -->
<meta-data
    android:name="android.support.customtabs.trusted.DEFAULT_URL"
    android:value="http://127.0.0.1:8000/" />

<!-- Стало (PROD): -->
<meta-data
    android:name="android.support.customtabs.trusted.DEFAULT_URL"
    android:value="https://ваш-домен.com/" />
```

**Включите autoVerify:**

```xml
<!-- Было (DEV): -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    
    <data
        android:scheme="http"
        android:host="127.0.0.1"
        android:port="8000" />
</intent-filter>

<!-- Стало (PROD): -->
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    
    <data
        android:scheme="https"
        android:host="ваш-домен.com" />
</intent-filter>
```

### Шаг 5: Обновить MainActivity.kt

**Откройте:** `twa-mobile/app/src/main/java/com/savushkin/scada/mobile/MainActivity.kt`

**Замените URL:**

```kotlin
// Было (DEV):
val twaUrl = "http://127.0.0.1:8000/"

// Стало (PROD):
val twaUrl = "https://ваш-домен.com/"
```

### Шаг 6: Собрать и установить приложение

```bash
cd twa-mobile

# Debug версия (с debug keystore)
./gradlew assembleDebug
./gradlew installDebug

# Release версия (с вашим keystore)
./gradlew assembleRelease
```

### Шаг 7: Проверить Digital Asset Links

**Официальный инструмент Google:**

```
https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://ваш-домен.com&relation=delegate_permission/common.handle_all_urls
```

**Должен вернуть:**

```json
{
  "statements": [
    {
      "source": {
        "web": {
          "site": "https://ваш-домен.com"
        }
      },
      "relation": "delegate_permission/common.handle_all_urls",
      "target": {
        "androidApp": {
          "packageName": "com.savushkin.scada.mobile",
          "certificate": {
            "sha256Fingerprint": "A5:42:03:8F:8F:29:DD:B8:C3:BF:CA:3C:9F:21:1D:9B:F4:82:13:18:A8:94:DB:EE:56:3F:25:D1:07:3E:2D:E4"
          }
        }
      }
    }
  ]
}
```

---

## ✅ Проверка работы TWA

### После деплоя

1. **Запустить приложение на Android устройстве**
2. **Проверить отсутствие полоски сверху**
   - ✅ Нет адресной строки
   - ✅ Нет кнопок навигации Chrome
   - ✅ Полноэкранный режим
3. **Проверить в Chrome DevTools (на ПК):**
   - Открыть: `chrome://inspect/#devices`
   - Найти ваше устройство
   - Открыть DevTools для TWA
   - Проверить в Console: `navigator.standalone` (должен быть `true`)

### Если полоска всё ещё видна

Подождите **до 24 часов** — Android кэширует результаты верификации Digital Asset Links.

**Ускорить проверку:**

```bash
# Очистить кэш Chrome на устройстве
# Settings -> Apps -> Chrome -> Storage -> Clear Cache

# Удалить и переустановить приложение
adb uninstall com.savushkin.scada.mobile
./gradlew installDebug
```

---

## 🔧 Troubleshooting

### Проблема: assetlinks.json недоступен (404)

**Решение:**

Убедитесь, что файл находится точно по пути:

```
pwa-app/.well-known/assetlinks.json
```

На сервере должен быть доступен:

```
https://ваш-домен.com/.well-known/assetlinks.json
```

**Для GitHub Pages:** убедитесь, что папка `.well-known` не игнорируется.

**Проверка в server.js:**

```javascript
// Уже настроено правильно в вашем server.js
const mimeTypes = {
  ".json": "application/json",
  // ...
};
```

### Проблема: SHA-256 не совпадает

**Решение:**

1. Получить актуальный SHA-256:

   ```bash
   cd twa-mobile
   ./gradlew signingReport
   ```

2. Обновить `assetlinks.json` с правильным отпечатком

3. Пересобрать и деплоить PWA

### Проблема: autoVerify не работает

**Возможные причины:**

1. ❌ Используется HTTP вместо HTTPS
2. ❌ Локальный адрес (127.0.0.1, localhost)
3. ❌ assetlinks.json недоступен
4. ❌ Неправильный SHA-256
5. ❌ Неправильный package name

**Проверка:**

```bash
# 1. Проверить доступность
curl https://ваш-домен.com/.well-known/assetlinks.json

# 2. Проверить через Google API
curl "https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://ваш-домен.com&relation=delegate_permission/common.handle_all_urls"

# 3. Проверить package name в AndroidManifest
grep package twa-mobile/app/src/main/AndroidManifest.xml
```

### Проблема: Полоска появляется периодически

**Причина:** Android периодически перепроверяет Digital Asset Links.

**Решение:** Убедитесь, что:

1. ✅ PWA всегда доступен по HTTPS
2. ✅ assetlinks.json всегда доступен
3. ✅ SSL сертификат не истёк

---

## 📚 Дополнительные ресурсы

- [Official TWA Documentation](https://developer.chrome.com/docs/android/trusted-web-activity/)
- [Digital Asset Links Guide](https://developers.google.com/digital-asset-links)
- [PWABuilder](https://www.pwabuilder.com/) — инструмент для автоматической генерации TWA
- [Bubblewrap](https://github.com/GoogleChromeLabs/bubblewrap) — CLI для создания TWA

---

## 📝 Краткая справка

### Текущая конфигурация (Development)

```yaml
PWA URL: http://127.0.0.1:8000/
Android Package: com.savushkin.scada.mobile
SHA-256 (debug): A5:42:03:8F:8F:29:DD:B8:C3:BF:CA:3C:9F:21:1D:9B:F4:82:13:18:A8:94:DB:EE:56:3F:25:D1:07:3E:2D:E4
Digital Asset Links: ❌ НЕ РАБОТАЕТ (локальный адрес)
Полоска сверху: ⚠️ ВИДНА (ожидаемо)
```

### Production конфигурация (после деплоя)

```yaml
PWA URL: https://ваш-домен.com/
Android Package: com.savushkin.scada.mobile
SHA-256: (тот же или новый для release)
Digital Asset Links: ✅ РАБОТАЕТ
Полоска сверху: ✅ СКРЫТА
```

---

## 🎯 Итого

1. **Локально:** Полоска сверху — это НОРМАЛЬНО (Digital Asset Links не работают с локальными адресами)
2. **Production:** После деплоя на HTTPS домен полоска исчезнет автоматически
3. **Ваш assetlinks.json уже корректный** — просто нужен публичный HTTPS домен
4. **Следуйте инструкции выше** для деплоя и всё заработает! 🚀
