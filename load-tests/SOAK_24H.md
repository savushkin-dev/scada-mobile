# Soak-тест 24 часа на сервере (НТ-6, #68)

Пошаговый runbook для самостоятельного развёртывания стенда и 24-часового
прогона на Linux-сервере. Всё делается штатными `make load-*` командами —
те же, что отработаны локально.

Цель: найти утечки памяти и «зависшие» WS-сессии. Нагрузка — 100 VU
комбинированного сценария (`combined.js`): 70% /ws/live, 20% /ws/unit,
8% REST-топология, 2% login/refresh. Каждая VU-итерация переподключается
каждые ~30 сек (`WS_SESSION_MS`) — это и есть эмуляция мобильных reconnect'ов,
требование #68 покрыто из коробки.

## 0. Требования к серверу

- Linux + Docker (с compose-плагином), git, GNU make, k6 v2+
  (установка k6: https://grafana.com/docs/k6/latest/set-up/install-k6/)
- Свободные порты: `5433` (postgres), `8081` (backend), `9090` (Prometheus),
  `3000` (Grafana). Если заняты — переопределяются переменными
  `LOAD_DB_PORT` / `LOAD_BACKEND_PORT` (см. Makefile).
- ~2 ГБ свободной памяти и ~5 ГБ диска (логи + метрики Prometheus за сутки).
- **Важно:** это изолированный стенд со своей БД на 5433. Прод не трогаем.

## 1. Развёртывание

```bash
git clone https://github.com/savushkin-dev/scada-mobile.git
cd scada-mobile
git checkout feat/51-load-testing-stand   # или main после мержа

make load-up        # postgres + миграции + сиды (500 польз.) + backend
make load-mon-up    # Prometheus :9090 + Grafana :3000 (admin/admin)
```

JWT-секреты сгенерируются автоматически в `backend/.env.dev` (как локально).

Проверка: `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`.

## 2. Обязательные шаги перед прогоном

```bash
make load-db-backup   # дамп БД в load-tests/backups/
make load-smoke       # smoke 5 VU / 2 мин — если падает, soak не начинать
```

Smoke должен завершиться с `checks_succeeded 100%` и без красных thresholds.

## 3. Запуск soak (24 часа)

Критично: k6 не должен умереть при разрыве SSH. Варианты:

- **tmux** (удобно, можно вернуться и посмотреть живой вывод):
  `tmux new -s soak` → команда ниже → отсоединиться `Ctrl+B`, `D`;
  вернуться: `tmux attach -t soak`
- либо **nohup**: `nohup make load-k6 ... > soak.log 2>&1 &`

Команда (плато 100 VU на 24 ч + плавные вход/выход, метрики k6 → Prometheus):

```bash
make load-k6 SCRIPT=load-tests/k6/combined.js \
  'STAGES=[{"duration":"10m","target":100},{"duration":"23h40m","target":100},{"duration":"10m","target":0}]' \
  K6_OUT="-o experimental-prometheus-rw" \
  K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
```

Summary по итогам упадёт в `load-tests/results/combined-<timestamp>.json`.

## 4. Наблюдение во время прогона

Grafana: дашборд **SCADA Loadtest (backend)** на `http://<server>:3000`
(если порт наружу не открыт — SSH-туннель: `ssh -L 3000:localhost:3000 user@server`).

**Снимки метрик (требование #68) — в начале (~1 ч), середине (~12 ч), конце (~24 ч):**

| Метрика | Что смотреть |
|---|---|
| `jvm_memory_used_bytes{area="heap",id="G1 Old Gen"}` | главный маркер утечки: не должна монотонно расти |
| `scada_ws_live_sessions`, `scada_ws_unit_sessions` | должны стабильно держаться около 70/20, без дрейфа вверх |
| `process_cpu_usage` | без постепенного роста |
| `http_server_requests_seconds` p95 | latency в конце ≈ latency в начале |

Плюс раз в несколько часов: `df -h` (диск под `backend/logs/` — за сутки
JSON-лог может вырасти до нескольких ГБ) и `docker ps` (контейнеры живы).

## 5. Критерии прохождения

- heap Old Gen в конце ≈ в начале (колебания норма, **монотонный рост — утечка**);
- после спада нагрузки `scada_ws_*_sessions` возвращаются в 0 (нет «зависших»);
- в логе нет растущего потока ERROR (`grep -c '"level":"ERROR"' backend/logs/scada.mobile.backend.json` в начале и в конце);
- k6: `http_req_failed` 0%, `ws_errors` < 0.1%, checks 100%.

## 6. После прогона

```bash
# забрать load-tests/results/combined-*.json + скриншоты дашборда (начало/середина/конец)
make load-db-backup   # дамп «после» — на всякий случай
make load-down        # или оставить стенд поднятым для повторных прогонов
```

Результаты и выводы идут в отчёт НТ-7 (#67).
