# 🚀 GitHub Actions Workflow для деплоя PWA

Документация по автоматическому деплою PWA на GitHub Pages из папки `pwa-app/`.

## 📋 Содержание

1. [Как работает workflow](#как-работает-workflow)
2. [Автоматические триггеры](#автоматические-триггеры)
3. [Безопасность и разрешения](#безопасность-и-разрешения)
4. [Этапы выполнения](#этапы-выполнения)
5. [Мониторинг и отладка](#мониторинг-и-отладка)
6. [Troubleshooting](#troubleshooting)

---

## 🔄 Как работает workflow

### Концепция

Workflow автоматически:

1. Проверяет наличие критических файлов в `pwa-app/`
2. Валидирует JSON файлы (manifest, assetlinks)
3. Устанавливает зависимости (если есть `package.json`)
4. Загружает содержимое `pwa-app/` на GitHub Pages
5. Сообщает о результатах

### Схема выполнения

```
┌─────────────────────────────────────────────────────────┐
│ Event: Push в main branch или ручной запуск            │
└──────────────────┬──────────────────────────────────────┘
                   │
        ┌──────────▼──────────┐
        │  Job 1: Prepare     │
        │  ─────────────────  │
        │  • Checkout code    │
        │  • Validate PWA     │
        │  • Check JSON files │
        └──────────────┬──────┘
                       │
        ┌──────────────▼──────────┐
        │  Job 2: Build           │
        │  ─────────────────      │
        │  • Setup Node.js        │
        │  • Install deps         │
        │  • Run lint (опция)     │
        │  • Upload artifacts     │
        └──────────────┬──────────┘
                       │
        ┌──────────────▼──────────────────┐
        │  Job 3: Deploy                  │
        │  ──────────────────────         │
        │  • Download artifacts           │
        │  • Setup GitHub Pages           │
        │  • Upload to Pages              │
        │  • Deploy & report              │
        └─────────────────────────────────┘
```

---

## 🎯 Автоматические триггеры

### Push в main branch

Workflow запускается автоматически при:

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'pwa-app/**'        # Только при изменении pwa-app
      - '.github/workflows/deploy-pwa.yml'  # Или самого workflow
```

**Пример:**

```bash
# Вносим изменения в pwa-app
echo "<!-- new comment -->" >> pwa-app/index.html

# Push в main
git add pwa-app/index.html
git commit -m "Update PWA UI"
git push origin main

# → Workflow запускается автоматически!
# → PWA задеплоивается через ~2-3 минуты
```

### Ручной запуск (workflow_dispatch)

Можно запустить вручную из GitHub Actions:

1. Откройте репозиторий на GitHub
2. Перейдите на вкладку **Actions**
3. Выберите **Deploy PWA to GitHub Pages**
4. Нажмите **Run workflow** → **Run workflow**
5. Workflow начнёт выполняться

**Полезно для:**

- Экстренного переразвёртывания
- Тестирования изменений
- Пересборки без новых коммитов

---

## 🔒 Безопасность и разрешения

### Permissions

```yaml
permissions:
  contents: read          # Чтение кода (checkout)
  pages: write            # Запись на GitHub Pages
  id-token: write         # OpenID Connect для деплоя
```

### Почему это безопасно

1. ✅ **Не требует Personal Access Token** — используется встроенный `GITHUB_TOKEN`
2. ✅ **Минимальные разрешения** — только чтение кода и запись на Pages
3. ✅ **OpenID Connect** — современный стандарт аутентификации
4. ✅ **Isolation по branch** — деплой только из `main`

### Environment Protection

```yaml
environment:
  name: github-pages
  url: ${{ steps.deployment.outputs.page_url }}
```

GitHub Pages автоматически обновляет environment после деплоя.

---

## 📝 Этапы выполнения

### Job 1: Prepare (Подготовка)

```bash
✓ Checkout repository
✓ Generate timestamp
✓ Validate PWA structure
✓ Validate JSON files
```

**Что проверяется:**

- Наличие `pwa-app/index.html`
- Наличие `pwa-app/manifest.webmanifest`
- Наличие `pwa-app/service-worker.js`
- Наличие `pwa-app/.well-known/assetlinks.json`
- Валидность JSON в manifest и assetlinks

**Если что-то не так:**

```
✗ ERROR: Required file missing: pwa-app/index.html
Exit with error code 1
```

---

### Job 2: Build (Сборка)

#### Шаг 1: Проверка наличия package.json

```bash
if [ -f "pwa-app/package.json" ]; then
  has_build=true
else
  has_build=false  # PWA используется как-есть
fi
```

#### Шаг 2: Setup Node.js (если есть package.json)

```yaml
- uses: actions/setup-node@v4
  with:
    node-version: '18'
    cache: 'npm'
    cache-dependency-path: 'pwa-app/package-lock.json'
```

- Устанавливает Node.js 18 LTS
- Кэширует npm packages для ускорения последующих запусков

#### Шаг 3: Install Dependencies

```bash
cd pwa-app
npm ci --omit=dev
```

- `npm ci` — "clean install" (детерминированная установка из package-lock.json)
- `--omit=dev` — пропускает dev dependencies для меньшего размера

#### Шаг 4: Lint (опционально)

```bash
if grep -q '"lint"' package.json; then
  npm run lint
fi
```

- Запускается только если в package.json есть скрипт `lint`
- `continue-on-error: true` — не блокирует деплой если линтинг не прошёл

#### Шаг 5: Upload Artifacts

```yaml
- uses: actions/upload-artifact@v3
  with:
    name: pwa-build
    path: pwa-app
    retention-days: 1
    if-no-files-found: error
```

- Сохраняет папку `pwa-app/` для следующего job (deploy)
- Хранится 1 день (достаточно для деплоя)
- Ошибка если папка не найдена

---

### Job 3: Deploy (Деплой)

#### Шаг 1: Download Artifacts

```yaml
- uses: actions/download-artifact@v3
  with:
    name: pwa-build
    path: ./pwa-app
```

#### Шаг 2: Prepare Deployment

```bash
ls -la pwa-app/
# Проверяем структуру перед публикацией
# ✓ index.html found
# ✓ manifest.webmanifest found
# ✓ .well-known/assetlinks.json found
```

#### Шаг 3: Setup Pages

```yaml
- uses: actions/configure-pages@v4
```

Готовит GitHub Pages к публикации.

#### Шаг 4: Upload to Pages

```yaml
- uses: actions/upload-pages-artifact@v2
  with:
    path: './pwa-app'
```

**ВАЖНО:** Загружается содержимое `pwa-app/`, не сама папка!

**Результат:**

```
https://savushkin-dev.github.io/scada-mobile/index.html
https://savushkin-dev.github.io/scada-mobile/manifest.webmanifest
https://savushkin-dev.github.io/scada-mobile/.well-known/assetlinks.json
```

#### Шаг 5: Deploy

```yaml
- uses: actions/deploy-pages@v2
```

Официальный action GitHub для деплоя Pages.

#### Шаг 6: Summary

```
═══════════════════════════════════════════════════════
✓ PWA successfully deployed to GitHub Pages!
═══════════════════════════════════════════════════════

📱 Access your PWA at:
   https://savushkin-dev.github.io/scada-mobile/

🔗 Digital Asset Links:
   https://savushkin-dev.github.io/scada-mobile/.well-known/assetlinks.json
```

---

## 📊 Мониторинг и отладка

### Просмотр логов

1. Откройте репозиторий на GitHub
2. **Actions** → **Deploy PWA to GitHub Pages**
3. Выберите последний запуск
4. Нажмите на job для раскрытия деталей

### Анализ деталей

```bash
# Пример логов успешного выполнения

Deploy to GitHub Pages
Preparing deployment
✓ Preparing PWA for deployment...

📦 PWA structure:
total 48
-rw-r--r--  1  runner runner  3204 Nov 21 14:32 index.html
-rw-r--r--  1  runner runner  2058 Nov 21 14:32 manifest.webmanifest
-rw-r--r--  1  runner runner  4521 Nov 21 14:32 service-worker.js
drwxr-xr-x  2  runner runner  4096 Nov 21 14:32 .well-known
drwxr-xr-x  2  runner runner  4096 Nov 21 14:32 assets
drwxr-xr-x  2  runner runner  4096 Nov 21 14:32 css
drwxr-xr-x  2  runner runner  4096 Nov 21 14:32 js

✓ index.html found
✓ manifest.webmanifest found
✓ .well-known/assetlinks.json found (TWA support)

✓ All files ready for deployment

Upload to GitHub Pages
Create artifact
✓ Upload to GitHub Pages: success

Deploy to GitHub Pages
Deploying commit abc1234 to github-pages environment
✓ Deployment successful
🐧 Deployment URL: https://savushkin-dev.github.io/scada-mobile/
```

### Частые ошибки

| Ошибка | Причина | Решение |
|--------|---------|--------|
| `Required file missing: pwa-app/index.html` | Файл не в папке `pwa-app/` | Проверить структуру, добавить файл |
| `manifest.webmanifest has invalid JSON` | Синтаксис ошибка в JSON | Открыть в редакторе, использовать `jq` для проверки |
| `npm ERR! code E404` | Пакет не найден в npm | Проверить версию в package.json |
| `continue-on-error: true` | Linteri провалился | Ошибка не блокирует деплой (намеренно) |

---

## 🔍 Troubleshooting

### Workflow не запускается

**Проблема:** Push в main, но workflow не видно в Actions.

**Решение:**

1. Проверить наличие файла `.github/workflows/deploy-pwa.yml` в main branch
2. Проверить синтаксис YAML (используйте [yamllint.com](https://www.yamllint.com/))
3. Пересоздать файл если нужно

### PWA не деплоится, ошибка в Job 1

**Проблема:** Validate PWA structure завершилась с ошибкой.

**Решение:**

```bash
# Проверьте локально
cd pwa-app
ls -la

# Должны быть файлы:
# - index.html
# - manifest.webmanifest
# - service-worker.js
# - .well-known/assetlinks.json
```

### JSON файлы имеют синтаксис ошибки

**Проблема:** `manifest.webmanifest has invalid JSON`

**Решение:**

```bash
# Локально проверьте
jq empty pwa-app/manifest.webmanifest
jq empty pwa-app/.well-known/assetlinks.json

# Или используйте VS Code для проверки (F1 → Format Document)
```

### GitHub Pages не обновляется

**Проблема:** Деплой прошёл, но Pages стоит на старой версии.

**Решение:**

1. Откройте GitHub Pages settings (репозиторий → Settings → Pages)
2. Проверьте что выбран branch `gh-pages` или подходящий
3. Очистите кэш браузера (Ctrl+Shift+Del) или откройте в инкогнито
4. Подождите 1-2 минуты (Github Pages может кэшировать)

### Как добавить Release SHA в assetlinks.json

Если вы хотите добавить Release keystore SHA:

1. Получите Release SHA-256:

   ```bash
   cd twa-mobile
   ./gradlew signingReport | grep SHA-256
   ```

2. Добавьте в `pwa-app/.well-known/assetlinks.json`:

   ```json
   {
     "sha256_cert_fingerprints": [
       "A5:42:03:...:E4",      // Debug SHA
       "NEW_RELEASE_SHA_HERE"  // Release SHA
     ]
   }
   ```

3. Push изменений → Workflow автоматически переразвёртывает

---

## 📚 Дополнительно

### Ручной деплой (без workflow)

Если нужно задеплоить без workflow:

```bash
# 1. Переключитесь на gh-pages branch
git checkout -b gh-pages

# 2. Скопируйте содержимое pwa-app в корень
cp -r pwa-app/* .
rm -rf pwa-app/  # Опционально (для чистоты)

# 3. Коммитьте
git add .
git commit -m "Deploy PWA from pwa-app"

# 4. Push
git push origin gh-pages

# 5. Готово! GitHub Pages автоматически развернёт
```

### Структура после деплоя

```
GitHub Pages (https://savushkin-dev.github.io/scada-mobile/)
├── index.html
├── manifest.webmanifest
├── service-worker.js
├── .well-known/
│   └── assetlinks.json
├── assets/
│   └── icons/
├── css/
│   └── styles.css
└── js/
    └── app.js
```

### Проверка деплоя

```bash
# Проверить доступность
curl -I https://savushkin-dev.github.io/scada-mobile/

# Ожидаемо:
# HTTP/2 200
# Content-Type: text/html; charset=utf-8

# Проверить assetlinks.json
curl -I https://savushkin-dev.github.io/scada-mobile/.well-known/assetlinks.json

# Ожидаемо:
# HTTP/2 200
# Content-Type: application/json
```

---

## 🎓 Итого

✅ **Workflow:**

- Автоматический при push в main
- Проверяет целостность PWA
- Деплоит на GitHub Pages
- Сообщает о результатах

✅ **Безопасность:**

- Минимальные разрешения
- Встроенная аутентификация GitHub
- Изоляция по branch

✅ **Надёжность:**

- 3 этапа проверки (prepare, build, deploy)
- Валидация JSON файлов
- Детальные логи

🚀 **Готово к production!**
