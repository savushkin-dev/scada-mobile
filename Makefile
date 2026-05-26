SHELL := /bin/sh

BACKEND_DIR := backend
FRONTEND_DIR := frontend
JAVA_OPTS := -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8
DEV_BACKEND_PORT ?= 8080
DEV_FRONTEND_PORT ?= 5500
SEED_DB_CONTAINER ?= scada-mobile-postgres
SEED_DB_NAME ?= scada_mobile
SEED_DB_USER ?= scada_user
SEED_DB_PASSWORD ?= scada_password
SEED_SQL ?= scripts/seed_notifications.sql
SEED_PROD_SQL ?= scripts/seed_prod_data.sql

ifeq ($(OS),Windows_NT)
GRADLEW := gradlew.bat
else
GRADLEW := ./gradlew
endif

.PHONY: help back-run back-stop back-run-prod front-install front-dev front-build db-seed db-seed-prod
.PHONY: bwa-init bwa-build-apk
.PHONY: docker-prod-up docker-prod-down docker-ps
.PHONY: fetch-logs fetch-logs-help

DOCKER_BASE_FILES := -f docker-compose.yml
DOCKER_PROD_FILES := -f docker-compose.prod.yml
PROD_ENV_FILE ?= .env.prod.local
PROD_ENV_FALLBACK := .env.prod.example
PROD_ENV_ACTIVE_FILE = $(if $(wildcard $(PROD_ENV_FILE)),$(PROD_ENV_FILE),$(PROD_ENV_FALLBACK))

ifeq ($(OS),Windows_NT)
help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend:"
	@echo "  make back-run       - run backend in background [dev profile, port $(DEV_BACKEND_PORT), Swagger enabled]"
	@echo "  make back-stop      - stop backend started by back-run"
	@echo "  make back-run-prod  - run backend [prod profile, port from SCADA_MOBILE_BACKEND_PORT, Swagger disabled]"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-prod-up   - start docker stack (prod mode) (env: PROD_ENV_FILE=.env.prod.local)"
	@echo "  make docker-prod-down - stop docker stack (prod mode)"
	@echo "  make docker-ps        - show container status for the active stack"
	@echo "  make db-seed          - seed database via docker exec (env: SEED_DB_CONTAINER, SEED_DB_NAME, SEED_DB_USER, SEED_DB_PASSWORD)"
	@echo "  make db-seed-prod     - seed production reference data (workshops, units, device_types, unit_devices) from env vars"
	@echo "  make fetch-logs       - archive backend logs and upload to transfer.sh (Linux server only)"
	@echo "  make fetch-logs-help  - show transfer.sh usage instructions"
	@echo ""
	@echo "Frontend:"
	@echo "  make front-install - install frontend dependencies"
	@echo "  make front-dev     - start frontend dev server (port $(DEV_FRONTEND_PORT), strict)"
	@echo "  make front-build   - build frontend for production"
	@echo ""
	@echo "Bubblewrap (Android):"
	@echo "  make bwa-init      - create/re-init TWA project in android folder"
	@echo "  make bwa-build-apk - build APK via bubblewrap"
else
help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend:"
	@echo "  make back-run       - run backend in background [dev profile, port $(DEV_BACKEND_PORT), Swagger enabled]"
	@echo "  make back-stop      - stop backend started by back-run"
	@echo "  make back-run-prod  - run backend [prod profile, port from SCADA_MOBILE_BACKEND_PORT, Swagger disabled]"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-prod-up   - start docker stack (prod mode) (env: PROD_ENV_FILE=.env.prod.local)"
	@echo "  make docker-prod-down - stop docker stack (prod mode)"
	@echo "  make docker-ps        - show container status for the active stack"
	@echo "  make db-seed          - seed database via docker exec (env: SEED_DB_CONTAINER, SEED_DB_NAME, SEED_DB_USER, SEED_DB_PASSWORD)"
	@echo "  make db-seed-prod     - seed production reference data (workshops, units, device_types, unit_devices) from env vars"
	@echo "  make fetch-logs       - archive backend logs and upload to transfer.sh (Linux server only)"
	@echo "  make fetch-logs-help  - show transfer.sh usage instructions"
	@echo ""
	@echo "Frontend:"
	@echo "  make front-install - install frontend dependencies"
	@echo "  make front-dev     - start frontend dev server (port $(DEV_FRONTEND_PORT), strict)"
	@echo "  make front-build   - build frontend for production"
	@echo ""
	@echo "Bubblewrap (Android):"
	@echo "  make bwa-init      - create/re-init TWA project in android folder"
	@echo "  make bwa-build-apk - build APK via bubblewrap"
endif

ifeq ($(OS),Windows_NT)
back-run:
	powershell -NoProfile -Command "$$env:JAVA_TOOL_OPTIONS='$(JAVA_OPTS)'; $$env:SPRING_PROFILES_ACTIVE='dev'; $$env:SERVER_PORT='$(DEV_BACKEND_PORT)'; $$env:SCADA_MOBILE_JWT_ACCESS_SECRET='$(SCADA_MOBILE_JWT_ACCESS_SECRET)'; $$env:SCADA_MOBILE_JWT_REFRESH_SECRET='$(SCADA_MOBILE_JWT_REFRESH_SECRET)'; $$p = Start-Process -FilePath '.\\gradlew.bat' -ArgumentList 'bootRun' -WorkingDirectory '$(BACKEND_DIR)' -PassThru; $$p.Id | Set-Content '$(BACKEND_DIR)\\.backend.pid'"

back-stop:
	powershell -NoProfile -Command "if (Test-Path '$(BACKEND_DIR)\\.backend.pid') { $$backendPid = Get-Content '$(BACKEND_DIR)\\.backend.pid'; Stop-Process -Id $$backendPid -Force; Remove-Item '$(BACKEND_DIR)\\.backend.pid' } else { Write-Host 'No backend PID file found.' }"

back-run-prod:
	cmd /V:ON /C "chcp 65001 >NUL & setlocal EnableDelayedExpansion & set "ENV_FILE=$(PROD_ENV_ACTIVE_FILE)" & (for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!ENV_FILE!") do (if not "%%A"=="" set "%%A=%%B")) & if "!SCADA_MOBILE_BACKEND_PORT!"=="" (echo Missing SCADA_MOBILE_BACKEND_PORT in !ENV_FILE!. & exit /b 1) & if "!SCADA_MOBILE_JWT_ACCESS_SECRET!"=="" (echo Missing SCADA_MOBILE_JWT_ACCESS_SECRET in !ENV_FILE!. & exit /b 1) & if "!SCADA_MOBILE_JWT_REFRESH_SECRET!"=="" (echo Missing SCADA_MOBILE_JWT_REFRESH_SECRET in !ENV_FILE!. & exit /b 1) & cd $(BACKEND_DIR) & set "JAVA_TOOL_OPTIONS=$(JAVA_OPTS)" & set "SPRING_PROFILES_ACTIVE=prod" & set "SERVER_PORT=!SCADA_MOBILE_BACKEND_PORT!" & set "SCADA_MOBILE_JWT_ACCESS_SECRET=!SCADA_MOBILE_JWT_ACCESS_SECRET!" & set "SCADA_MOBILE_JWT_REFRESH_SECRET=!SCADA_MOBILE_JWT_REFRESH_SECRET!" & $(GRADLEW) bootRun"

db-seed:
	cmd /V:ON /C "set \"SEED_DB_PASSWORD=$(SEED_DB_PASSWORD)\" & if not exist $(SEED_SQL) (echo Missing $(SEED_SQL). & exit /b 1) else if \"!SEED_DB_PASSWORD!\"==\"\" (echo Missing SEED_DB_PASSWORD. & exit /b 1) else (docker exec -i -e PGPASSWORD=!SEED_DB_PASSWORD! $(SEED_DB_CONTAINER) psql -U $(SEED_DB_USER) -d $(SEED_DB_NAME) -v ON_ERROR_STOP=1 -f - < $(SEED_SQL))"

db-seed-prod:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts\seed_prod.ps1 -EnvFile '$(PROD_ENV_ACTIVE_FILE)' -SeedSql '$(SEED_PROD_SQL)' -Container '$(SEED_DB_CONTAINER)'
else
back-run:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=dev SERVER_PORT='$(DEV_BACKEND_PORT)' SCADA_MOBILE_JWT_ACCESS_SECRET='$(SCADA_MOBILE_JWT_ACCESS_SECRET)' SCADA_MOBILE_JWT_REFRESH_SECRET='$(SCADA_MOBILE_JWT_REFRESH_SECRET)' nohup $(GRADLEW) bootRun > .backend.log 2>&1 & echo $$! > .backend.pid

back-stop:
	@if [ -f "$(BACKEND_DIR)/.backend.pid" ]; then \
		kill $$(cat "$(BACKEND_DIR)/.backend.pid") && rm "$(BACKEND_DIR)/.backend.pid"; \
	else \
		echo "No backend PID file found."; \
	fi

back-run-prod:
	@set -a; \
	. "$(PROD_ENV_ACTIVE_FILE)"; \
	set +a; \
	if [ -z "$$SCADA_MOBILE_BACKEND_PORT" ]; then \
		echo "Missing SCADA_MOBILE_BACKEND_PORT in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	if [ -z "$$SCADA_MOBILE_JWT_ACCESS_SECRET" ]; then \
		echo "Missing SCADA_MOBILE_JWT_ACCESS_SECRET in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	if [ -z "$$SCADA_MOBILE_JWT_REFRESH_SECRET" ]; then \
		echo "Missing SCADA_MOBILE_JWT_REFRESH_SECRET in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=prod SERVER_PORT="$$SCADA_MOBILE_BACKEND_PORT" SCADA_MOBILE_JWT_ACCESS_SECRET="$$SCADA_MOBILE_JWT_ACCESS_SECRET" SCADA_MOBILE_JWT_REFRESH_SECRET="$$SCADA_MOBILE_JWT_REFRESH_SECRET" $(GRADLEW) bootRun

db-seed:
	@if [ ! -f "$(SEED_SQL)" ]; then \
		echo "Missing $(SEED_SQL)."; \
		exit 1; \
	fi
	@if [ -z "$(SEED_DB_PASSWORD)" ]; then \
		echo "Missing SEED_DB_PASSWORD."; \
		exit 1; \
	fi
	@docker exec -i -e PGPASSWORD="$(SEED_DB_PASSWORD)" "$(SEED_DB_CONTAINER)" \
		psql -U "$(SEED_DB_USER)" -d "$(SEED_DB_NAME)" -v ON_ERROR_STOP=1 -f - < "$(SEED_SQL)"

db-seed-prod:
	@if [ ! -f "$(SEED_PROD_SQL)" ]; then \
		echo "Missing $(SEED_PROD_SQL)."; \
		exit 1; \
	fi
	@if ! docker inspect --format='{{.State.Running}}' "$(SEED_DB_CONTAINER)" >/dev/null 2>&1; then \
		echo "Container $(SEED_DB_CONTAINER) is not running. Start it first: make docker-prod-up"; \
		exit 1; \
	fi
	@while IFS='=' read -r key val; do \
		case "$$key" in \#*|'') continue ;; esac; \
		export "$$key=$$val"; \
	done < "$(PROD_ENV_ACTIVE_FILE)"; \
	if [ -z "$$SCADA_MOBILE_POSTGRES_DB" ]; then \
		echo "Missing SCADA_MOBILE_POSTGRES_DB in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	if [ -z "$$SCADA_MOBILE_POSTGRES_USER" ]; then \
		echo "Missing SCADA_MOBILE_POSTGRES_USER in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	if [ -z "$$SCADA_MOBILE_DATABASE_PASSWORD" ]; then \
		echo "Missing SCADA_MOBILE_DATABASE_PASSWORD in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	docker exec -i -e PGPASSWORD="$$SCADA_MOBILE_DATABASE_PASSWORD" "$(SEED_DB_CONTAINER)" \
		psql -U "$$SCADA_MOBILE_POSTGRES_USER" -d "$$SCADA_MOBILE_POSTGRES_DB" -v ON_ERROR_STOP=1 -f - < "$(SEED_PROD_SQL)"
endif

ifeq ($(OS),Windows_NT)
front-install:
	cmd /C "cd $(FRONTEND_DIR) && npm install"

front-dev:
	cmd /C "cd $(FRONTEND_DIR) && npm run dev -- --port $(DEV_FRONTEND_PORT) --strictPort"

front-build:
	cmd /C "cd $(FRONTEND_DIR) && npm run build"

bwa-init:
	cmd /C "cd android && npx @bubblewrap/cli init --manifest https://scada-savushkin-dev.netlify.app/manifest.webmanifest"

bwa-build-apk:
	cmd /C "cd android && npx @bubblewrap/cli build"
else
front-install:
	cd $(FRONTEND_DIR) && if [ -f package-lock.json ]; then npm ci; else npm install; fi

front-dev:
	cd $(FRONTEND_DIR) && npm run dev -- --port $(DEV_FRONTEND_PORT) --strictPort

front-build:
	cd $(FRONTEND_DIR) && npm run build

bwa-init:
	cd android && npx @bubblewrap/cli init --manifest https://scada-savushkin-dev.netlify.app/manifest.webmanifest

bwa-build-apk:
	cd android && npx @bubblewrap/cli build
endif

ifeq ($(OS),Windows_NT)
docker-prod-up:
	cmd /C "if not exist $(PROD_ENV_FILE) (echo Missing $(PROD_ENV_FILE). Copy .env.prod.example -^> $(PROD_ENV_FILE) and fill values. & exit /b 1) else (docker compose --env-file $(PROD_ENV_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) up --build -d)"

docker-prod-down:
	cmd /C "docker compose --env-file $(PROD_ENV_ACTIVE_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) down"

docker-ps:
	-cmd /C "docker compose --env-file $(PROD_ENV_ACTIVE_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) ps"
else
docker-prod-up:
	@if [ ! -f "$(PROD_ENV_FILE)" ]; then \
		echo "Missing $(PROD_ENV_FILE). Copy .env.prod.example -> $(PROD_ENV_FILE) and fill values."; \
		exit 1; \
	fi
	docker compose --env-file "$(PROD_ENV_FILE)" $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) up --build -d

docker-prod-down:
	docker compose --env-file "$(PROD_ENV_ACTIVE_FILE)" $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) down

docker-ps:
	-docker compose --env-file "$(PROD_ENV_ACTIVE_FILE)" $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) ps

fetch-logs:
	@if [ ! -d "$(BACKEND_DIR)/logs" ]; then \
		echo "Ошибка: папка $(BACKEND_DIR)/logs не найдена. Убедитесь, что backend запущен и volume с логами смонтирован."; \
		exit 1; \
	fi
	@if [ -z "$$(ls -A $(BACKEND_DIR)/logs 2>/dev/null)" ]; then \
		echo "Ошибка: папка $(BACKEND_DIR)/logs существует, но пуста. Подождите минуту-две, пока накопятся логи."; \
		exit 1; \
	fi
	@TIMESTAMP=$$(date +%Y%m%d_%H%M%S); \
	ARCHIVE="/tmp/scada_mobile_logs_$$TIMESTAMP.tar.gz"; \
	echo "Архивируем логи..."; \
	tar -czf "$$ARCHIVE" -C "$(BACKEND_DIR)" logs/; \
	echo "Загружаем на transfer.sh..."; \
	URL=$$(curl --silent --upload-file "$$ARCHIVE" "https://transfer.sh/scada_mobile_logs.tar.gz"); \
	rm -f "$$ARCHIVE"; \
	echo ""; \
	echo "$$URL"; \
	echo ""; \
	echo "Логи заархивированы и загружены. Ссылка действительна 14 дней."

fetch-logs-help:
	@echo "Использование: make fetch-logs"
	@echo ""
	@echo "Команда архивирует содержимое backend/logs/ и загружает на transfer.sh."
	@echo "Требования:"
	@echo "  - backend должен быть запущен через docker compose (volume с логами смонтирован)"
	@echo "  - в папке backend/logs/ должны быть файлы (подождите минуту после старта)"
	@echo "  - сервер должен иметь доступ к transfer.sh (порт 443)"
	@echo ""
	@echo "Если transfer.sh недоступен (корпоративный файрвол):"
	@echo "  - заархивируйте вручную: tar -czf logs.tar.gz backend/logs/"
	@echo "  - передайте разработчику любым доступным способом"
endif
