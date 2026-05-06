# 📱 Android TWA — SCADA Mobile

Android-оболочка реализована с использованием технологии **Trusted Web Activity (TWA)**, которая запускает PWA-приложение в нативном Android-контейнере без адресной строки браузера.

> Технологический стек проекта, ключевые принципы и архитектура — в [`STRUCTURE.md`](../STRUCTURE.md).

## Purpose
Описание Android-оболочки TWA, сборки и выпуска.

## Table of contents
- [Purpose](#purpose)
- [Структура проекта](#структура-проекта)
- [Сборка и запуск](#сборка-и-запуск)
- [Установка готового APK](#установка-готового-apk)

---

## 📂 Структура проекта

Ключевые файлы в проекте:

-   `app/src/main/java/com/savushkin/scada/mobile/LauncherActivity.java`: Основная активность, которая наследуется от `com.google.androidbrowserhelper.trusted.LauncherActivity` и запускает TWA.
-   `app/src/main/java/com/savushkin/scada/mobile/Application.java`: Кастомный класс `Application` для инициализации компонентов приложения.
-   `app/src/main/java/com/savushkin/scada/mobile/DelegationService.java`: Сервис для обработки делегирования разрешений из веб-контента в нативную часть.
-   `app/build.gradle`: Конфигурационный файл сборки Gradle, содержащий зависимости (например, `com.google.androidbrowserhelper:androidbrowserhelper`) и настройки TWA.
-   `app/src/main/res/values/strings.xml`: Ресурсы строк, где указывается URL-адрес для запуска веб-приложения (`launching_url`).
-   `app/src/main/AndroidManifest.xml`: Манифест приложения, где настраиваются мета-данные для TWA.

---

## 🛠️ Сборка и запуск

#### 1. Предварительные требования
-   Android Studio
-   Веб-приложение (PWA), доступное по HTTPS

#### 2. Настройка проекта
1.  **Клонируйте репозиторий:**
    ```bash
    git clone <URL репозитория>
    ```
2.  **Откройте проект в Android Studio.**
3.  **Настройте URL:** В файле `app/src/main/res/values/strings.xml` укажите корректный адрес вашего веб-приложения в ресурсе `launching_url`.
4.  **Настройте Digital Asset Links:**
    -   Сгенерируйте **SHA-256 отпечаток** вашего ключа подписи приложения. Это можно сделать через Android Studio (`Build > Generate Signed Bundle / APK`) или с помощью `keytool`.
    -   Разместите файл `assetlinks.json` в корне вашего веб-сайта в директории `/.well-known/`. Файл должен содержать package name вашего приложения и SHA-256 отпечаток.
    
    Пример `assetlinks.json`:
    ```json
    [{
      "relation": ["delegate_permission/common.handle_all_urls"],
      "target": {
        "namespace": "android_app",
        "package_name": "com.savushkin.scada.mobile",
        "sha256_cert_fingerprints":
        ["XX:XX:XX:...:XX"]
      }
    }]
    ```

#### 3. Сборка
-   **Debug:** Выполните сборку `debug` варианта через Android Studio или с помощью Gradle:
    ```bash
    ./gradlew assembleDebug
    ```
-   **Release:** Для сборки подписанной release-версии используйте `Build > Generate Signed Bundle / APK` в Android Studio.

#### 4. Запуск
Запустите приложение на эмуляторе или реальном устройстве Android. Если Digital Asset Links настроены правильно, приложение откроется в полноэкранном режиме без адресной строки браузера.

---

## 📲 Установка готового APK

Актуальный подписанный APK доступен в [разделе Releases](https://github.com/savushkin-dev/scada-mobile/releases/latest/download/app-release-signed.apk).

**Для установки на Android-устройство:**

1. Загрузите APK с раздела Releases репозитория
2. Передайте APK на устройство (email, облако, Bluetooth, кабель)
3. Откройте файловый менеджер, найдите APK и нажмите установку
4. Подтвердите установку (может потребоваться разрешение на установку из неизвестных источников в настройках)
5. Приложение появится на главном экране

**Проверка после установки:**

- **Запуск:** Приложение должно открываться без адресной строки браузера
- **Отображение данных:** Корректное отображение данных SCADA в реальном времени
- **Офлайн режим:** При отключении интернета ранее загруженные данные остаются доступны благодаря Service Worker
- **Ответ сервера:** Приложение получает актуальные данные при наличии соединения

> Политика безопасности (что нельзя коммитить, как хранить keystore) — в [`SECURITY.md`](../SECURITY.md).
