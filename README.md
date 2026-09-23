*[English version here](README.en.md)*

# 🥛 SCADA Mobile

![Backend](https://img.shields.io/badge/backend-Spring%20Boot%204%20%2F%20Java%2021-6DB33F?style=flat-square)
![Frontend](https://img.shields.io/badge/frontend-React%20%2B%20TypeScript%20%2B%20Vite-61DAFB?style=flat-square)
![Database](https://img.shields.io/badge/database-PostgreSQL-336791?style=flat-square)
![Deploy](https://img.shields.io/badge/deploy-Docker-2496ED?style=flat-square)

## 📌 Описание

**SCADA Mobile** — система оперативного оповещения сотрудников крупного молочного предприятия о простоях и ЧП на производственных линиях.

Когда линия или оборудование останавливается, сотрудники в других цехах часто не понимают причину (закончились заготовки/материалы, оператор отвлёкся, узел стоит) — и «эффект домино» останавливает упаковку, палетирование и отгрузку. Приложение быстро доносит всем заинтересованным сотрудникам, что произошло, где и почему участок стоит.

Общий пайплайн: событие на оборудовании → считывание через PrintSrv → backend на Spring агрегирует и нормализует событие → рассылка уведомлений → отображение в веб-клиенте.

## 🎯 Возможности

- **Главная панель** — список цехов с состоянием (норма/проблема) и количеством аппаратов и линий.
- **Автоматы цеха** — карточки автоматов с данными текущей партии и значками активных уведомлений.
- **Карточка автомата** — вкладки «Партия», «Устройства», «Очередь», «Журнал»: текущая партия (описание, EAN, номер, даты выработки/годности), состояние периферии, очередь команд, история ошибок.
- **Уведомления** — вызов ответственных сотрудников прямо с карточки автомата; входящие уведомления от других сотрудников с вибрацией на устройстве.
- **Админ-панель** — управление сотрудниками (роли, табельные номера, привязка автоматов) и автоматами.
- **PWA** — веб-клиент устанавливается на устройство и доставляет уведомления через Service Worker, без отдельного нативного приложения.

## 🖼️ Скриншоты

<div align="center">

<table>
  <tr>
    <td align="center">
      <b>1️⃣ Список цехов</b><br>
      <img src="screenshots/workshops_list.png" alt="Список цехов" width="230"/>
    </td>
    <td align="center">
      <b>2️⃣ Автоматы цеха</b><br>
      <img src="screenshots/workshop_units_notification.png" alt="Автоматы цеха со значком уведомления" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>3️⃣ Список автоматов внутри цеха</b><br>
      <img src="screenshots/список_автоматов_внутри_цеха.png" alt="Список автоматов внутри цеха" width="230"/>
    </td>
    <td align="center">
      <b>4️⃣ Вызов уведомления ответственным</b><br>
      <img src="screenshots/список_автоматов_внутри_цеха_в_состоянии_отобвинутой_карточки_автомата_чтобы_вызвать_уведомления_всем_ответственным_сотрудникам.png" alt="Нажатие на карточку автомата для вызова уведомления ответственным сотрудникам" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>5️⃣ Карточка автомата, вкладка «Партия»</b><br>
      <img src="screenshots/unit_details_batch.png" alt="Карточка автомата, вкладка «Партия»" width="230"/>
    </td>
    <td align="center">
      <b>6️⃣ Вкладка «Партия» с включённым уведомлением</b><br>
      <img src="screenshots/экран_деталей_автомата_вкладка_партия_с_включенным_уведомлением.png" alt="Карточка автомата, вкладка «Партия» с включённым уведомлением" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>7️⃣ Вкладка «Устройства»</b><br>
      <img src="screenshots/экран_деталей_автомата_вкладка_устройства.png" alt="Карточка автомата, вкладка «Устройства»" width="230"/>
    </td>
    <td align="center">
      <b>8️⃣ Вкладка «Очередь»</b><br>
      <img src="screenshots/экран_деталей_автомата_вкладка_очередь.png" alt="Карточка автомата, вкладка «Очередь»" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>9️⃣ Вкладка «Ошибки», без ошибок</b><br>
      <img src="screenshots/экран_деталей_автомата_вкладка_ошибки_без_ошибок.png" alt="Карточка автомата, вкладка «Ошибки» без активных ошибок" width="230"/>
    </td>
    <td align="center">
      <b>🔟 Вкладка «Ошибки» с активной ошибкой</b><br>
      <img src="screenshots/экран_деталей_автомата_вкладка_ошибки_с_одной_активной_ошибкой.png" alt="Карточка автомата, вкладка «Ошибки» с одной активной ошибкой" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>1️⃣1️⃣ Подтверждение уведомления</b><br>
      <img src="screenshots/overlay-окно_с_подтверждением_желания_снять_или_отправить_уведомление.png" alt="Окно подтверждения снятия или отправки уведомления" width="230"/>
    </td>
    <td align="center">
      <b>1️⃣2️⃣ Вкладка уведомлений</b><br>
      <img src="screenshots/notifications_tab.png" alt="Вкладка уведомлений" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>1️⃣3️⃣ Уведомления от сотрудников (пусто)</b><br>
      <img src="screenshots/вкладка_уведомлений_от_других_сотрудников_без_уведомлений.png" alt="Вкладка уведомлений от других сотрудников без уведомлений" width="230"/>
    </td>
    <td align="center">
      <b>1️⃣4️⃣ Экран входа</b><br>
      <img src="screenshots/экран_входа.png" alt="Экран входа" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>1️⃣5️⃣ Страница пользователя</b><br>
      <img src="screenshots/страница_пользователя.png" alt="Страница пользователя" width="230"/>
    </td>
    <td align="center">
      <b>1️⃣6️⃣ Настройки уведомлений пользователя</b><br>
      <img src="screenshots/страница_пользователя_настройки_уведомлений.png" alt="Страница пользователя, настройки уведомлений" width="230"/>
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>1️⃣7️⃣ Админ-панель: главная страница</b><br>
      <img src="screenshots/админ_панель_главная_страница.png" alt="Админ-панель, главная страница" width="230"/>
    </td>
    <td></td>
  </tr>
</table>

</div>

## 🛠️ Состав системы и технологии

- **[backend](backend)** — Spring Boot 4 / Java 21: REST + WebSocket API, JWT-аутентификация, API админ-панели, health-check, интеграция с PrintSrv, Flyway-миграции, PostgreSQL.
- **[frontend](frontend)** — React + TypeScript + Vite (PWA), админка на React Admin.
- **Docker** — docker-compose сценарии для prod-запуска уже в репозитории; в планах оркестрация (Kubernetes) во внутренней сети компании.

## 📋 Состояние проекта (август 2026)

- Базовые части работают: backend, веб-клиент (PWA), админ-панель, уведомления.
- **TWA/Android.** Нативная оболочка (Bubblewrap) выведена из репозитория: ключевая потребность (уведомления на устройствах с вибрацией) закрывается веб-клиентом в Chrome через Service Worker, а для полноценного TWA в корпоративной сети требуется строгий HTTPS, что организационно сложно. Остаётся потенциальной опцией на будущее.
- **Планируется миграция на Kafka.** Прямой TCP-опрос PrintServer заменяется чтением из Kafka — опрос берёт на себя отдельный Gateway. См. [ТЗ_миграция_на_Kafka.md](ТЗ_миграция_на_Kafka.md) и [ТЗ_Gateway_Якимовец.md](ТЗ_Gateway_Якимовец.md).

## 🚀 Быстрый локальный запуск

### Что нужно установить

- Java 21+
- Node.js 20+ и npm
- Docker Desktop (только для prod режима)

### Вариант 1. Запуск по частям (рекомендуемый для разработки)

1. Запустите backend: `make back-run`.
2. В другом терминале установите зависимости frontend (один раз): `make front-install`.
3. Запустите frontend: `make front-dev`.
4. Откройте в браузере:
   - Frontend: <http://localhost:5500>
   - Backend health: <http://localhost:8080/api/v1.0.0/health/live>
   - Swagger UI (dev): <http://localhost:8080/swagger-ui.html>

### Вариант 2. Запуск в Docker (только prod)

Запуск: `make docker-prod-up`. Остановка: `make docker-prod-down`.

Подробно про Docker, prod-профиль и env-файлы: [RUN_PROJECT_DOCKER.md](RUN_PROJECT_DOCKER.md).

Полный список шорткатов: `make help` (см. [MAKEFILE.md](MAKEFILE.md)).

## 📚 Документация

Чтобы не дублировать информацию, детализация разнесена по отдельным файлам:

- [STRUCTURE.md](STRUCTURE.md) — архитектура проекта и технологический стек;
- [PROJECT_DIAGRAM.md](PROJECT_DIAGRAM.md) — визуальные схемы системы;
- [API_REFERENCE.md](API_REFERENCE.md) — контракт REST и WebSocket;
- [FRONTEND_API.md](FRONTEND_API.md) — краткий обзор фронтенд-использования API;
- [FRONTEND_DATA_SOURCES.md](FRONTEND_DATA_SOURCES.md) — откуда backend берёт данные для фронтенда;
- [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md) — архитектура frontend;
- [NOTIFICATIONS_ARCHITECTURE.md](NOTIFICATIONS_ARCHITECTURE.md) — логика уведомлений;
- [api_mapping.md](api_mapping.md) — разделение транспортов и маппинг;
- [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md) — фактическая архитектура backend;
- [BACKEND_COMPONENT_DIAGRAM.md](BACKEND_COMPONENT_DIAGRAM.md) — компонентная диаграмма backend;
- [BACKEND_DATA_FLOW.md](BACKEND_DATA_FLOW.md) — поток данных в backend;
- [AUTH_FLOW.md](AUTH_FLOW.md) — аутентификация и авторизация;
- [ALERT_LIFECYCLE.md](ALERT_LIFECYCLE.md) — жизненный цикл алерта;
- [SECURITY.md](SECURITY.md) — политика безопасности и JWT;
- [TEST_ACCOUNTS.md](TEST_ACCOUNTS.md) — тестовые аккаунты и данные для проверок;
- [MAKEFILE.md](MAKEFILE.md) — команды разработки и запуска;
- [frontend/README.md](frontend/README.md) — детали веб-клиента и фронтенд-команд.
