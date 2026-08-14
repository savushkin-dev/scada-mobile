SHELL := /bin/sh

BACKEND_DIR := backend
FRONTEND_DIR := frontend
JAVA_OPTS := -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8
DEV_BACKEND_PORT ?= 8080
DEV_FRONTEND_PORT ?= 5500
SEED_DB_CONTAINER_DEV ?= postgres
SEED_DB_CONTAINER_PROD ?= scada-mobile-postgres
SEED_DB_NAME ?= scada_mobile
SEED_DB_USER ?= scada_user
SEED_DB_PASSWORD ?= scada_password
SEED_SQL ?= scripts/seed_notifications.sql
SEED_PROD_SQL ?= scripts/seed_prod_data.sql

# Нагрузочное тестирование (эпик #51, НТ-1): изолированный стенд.
# Свой postgres (порт 5433) и свой backend (порт 8081, профиль loadtest),
# чтобы не пересекаться с dev-окружением (5432/8080).
LOAD_DB_CONTAINER ?= scada-loadtest-postgres
LOAD_DB_PORT ?= 5433
LOAD_DB_NAME ?= scada_mobile
LOAD_DB_USER ?= scada_user
LOAD_DB_PASSWORD ?= scada_password
LOAD_DB_VOLUME ?= scada-loadtest-pgdata
LOAD_BACKEND_PORT ?= 8081
LOAD_BACKEND_LOG := .backend-loadtest.log
LOAD_BACKEND_PID := .backend-loadtest.pid
LOAD_BACKUP_DIR := load-tests/backups
LOAD_SEED_SQL ?= scripts/seed_notifications.sql scripts/seed_loadtest_users.sql

# Локальный файл с автогенерируемыми dev JWT-секретами (игнорируется git через .env.*)
DEV_SECRETS_FILE := .env.dev
DEV_BACKEND_LOG := .backend.log
API_BASE_PATH ?= /api/v1.0.0

# POSIX-окружение (Linux/macOS/Git Bash/MSYS/Cygwin) определяем по uname,
# а не по переменной OS: в Git Bash OS=Windows_NT, но доступны sh-утилиты.
UNAME_S := $(shell uname -s 2>/dev/null)
IS_POSIX := $(if $(UNAME_S),1,)
# Git Bash/MSYS/Cygwin на Windows
IS_MINGW := $(if $(filter MINGW% MSYS% CYGWIN%,$(UNAME_S)),1,)

ifeq ($(IS_POSIX),)
GRADLEW := gradlew.bat
else
GRADLEW := ./gradlew
endif

.PHONY: help back-run back-stop back-wait back-logs back-run-prod front-install front-dev front-build db-seed db-seed-prod
.PHONY: bwa-init bwa-build-apk
.PHONY: docker-prod-up docker-prod-down docker-ps
.PHONY: load-up load-down load-db-up load-db-down load-db-reset load-db-seed load-db-backup load-db-restore
.PHONY: load-back-run load-back-stop load-back-wait load-back-logs

DOCKER_COMPOSE_FILE := -f docker-compose.yml
PROD_ENV_FILE ?= .env.prod.local
PROD_ENV_FALLBACK := .env.prod.example
PROD_ENV_ACTIVE_FILE = $(if $(wildcard $(PROD_ENV_FILE)),$(PROD_ENV_FILE),$(PROD_ENV_FALLBACK))

help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend:"
	@echo "  make back-run       - run backend in background [dev profile, port $(DEV_BACKEND_PORT), Swagger enabled]"
	@echo "  make back-stop      - stop backend started by back-run (also kills the listener on port $(DEV_BACKEND_PORT))"
	@echo "  make back-wait      - wait until backend responds on port $(DEV_BACKEND_PORT)"
	@echo "  make back-logs      - tail backend log ($(BACKEND_DIR)/$(DEV_BACKEND_LOG))"
	@echo "  make back-run-prod  - run backend [prod profile, port from SCADA_MOBILE_BACKEND_PORT, Swagger disabled]"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-prod-up   - start docker stack (prod mode) (env: PROD_ENV_FILE=.env.prod.local)"
	@echo "  make docker-prod-down - stop docker stack (prod mode)"
	@echo "  make docker-ps        - show container status for the active stack"
	@echo "  make db-seed          - seed dev database via docker exec (container: $(SEED_DB_CONTAINER_DEV), env: SEED_DB_NAME, SEED_DB_USER, SEED_DB_PASSWORD)"
	@echo "  make db-seed-prod     - seed production database (container: $(SEED_DB_CONTAINER_PROD), workshops/units/device_types from env vars)"
	@echo ""
	@echo "Load testing (stand: postgres :$(LOAD_DB_PORT), backend :$(LOAD_BACKEND_PORT), profile loadtest):"
	@echo "  make load-up          - start the whole stand (db + migrations + seed + backend with mock PrintSrv)"
	@echo "  make load-down        - stop backend and remove stand postgres container (volume kept)"
	@echo "  make load-db-up       - start stand postgres container only"
	@echo "  make load-db-seed     - apply topology + 500 load users to stand DB"
	@echo "  make load-db-backup   - pg_dump stand DB into $(LOAD_BACKUP_DIR)/ (run before load tests)"
	@echo "  make load-db-restore  - restore stand DB: make load-db-restore FILE=$(LOAD_BACKUP_DIR)/xxx.sql"
	@echo "  make load-db-reset    - remove container AND volume (clean slate)"
	@echo "  make load-back-run    - start backend in loadtest profile (mock PrintSrv, port $(LOAD_BACKEND_PORT))"
	@echo "  make load-back-stop   - stop loadtest backend"
	@echo "  make load-back-wait   - wait until loadtest backend responds"
	@echo "  make load-back-logs   - tail loadtest backend log ($(BACKEND_DIR)/$(LOAD_BACKEND_LOG))"
	@echo ""
	@echo "Frontend:"
	@echo "  make front-install - install frontend dependencies"
	@echo "  make front-dev     - start frontend dev server (port $(DEV_FRONTEND_PORT), strict)"
	@echo "  make front-build   - build frontend for production"
	@echo ""
	@echo "Bubblewrap (Android):"
	@echo "  make bwa-init      - create/re-init TWA project in android folder"
	@echo "  make bwa-build-apk - build APK via bubblewrap"

ifeq ($(IS_POSIX),)
back-run:
	powershell -NoProfile -Command "$$env:JAVA_TOOL_OPTIONS='$(JAVA_OPTS)'; $$env:SPRING_PROFILES_ACTIVE='dev'; $$env:SERVER_PORT='$(DEV_BACKEND_PORT)'; $$env:SCADA_MOBILE_JWT_ACCESS_SECRET='$(SCADA_MOBILE_JWT_ACCESS_SECRET)'; $$env:SCADA_MOBILE_JWT_REFRESH_SECRET='$(SCADA_MOBILE_JWT_REFRESH_SECRET)'; $$p = Start-Process -FilePath '.\\gradlew.bat' -ArgumentList 'bootRun' -WorkingDirectory '$(BACKEND_DIR)' -PassThru; $$p.Id | Set-Content '$(BACKEND_DIR)\\.backend.pid'"

back-stop:
	powershell -NoProfile -Command "if (Test-Path '$(BACKEND_DIR)\\.backend.pid') { $$backendPid = Get-Content '$(BACKEND_DIR)\\.backend.pid'; Stop-Process -Id $$backendPid -Force; Remove-Item '$(BACKEND_DIR)\\.backend.pid' } else { Write-Host 'No backend PID file found.' }"

back-run-prod:
	cmd /V:ON /C "chcp 65001 >NUL & setlocal EnableDelayedExpansion & set "ENV_FILE=$(PROD_ENV_ACTIVE_FILE)" & (for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!ENV_FILE!") do (if not "%%A"=="" set "%%A=%%B")) & if "!SCADA_MOBILE_BACKEND_PORT!"=="" (echo Missing SCADA_MOBILE_BACKEND_PORT in !ENV_FILE!. & exit /b 1) & if "!SCADA_MOBILE_JWT_ACCESS_SECRET!"=="" (echo Missing SCADA_MOBILE_JWT_ACCESS_SECRET in !ENV_FILE!. & exit /b 1) & if "!SCADA_MOBILE_JWT_REFRESH_SECRET!"=="" (echo Missing SCADA_MOBILE_JWT_REFRESH_SECRET in !ENV_FILE!. & exit /b 1) & cd $(BACKEND_DIR) & set "JAVA_TOOL_OPTIONS=$(JAVA_OPTS)" & set "SPRING_PROFILES_ACTIVE=prod" & set "SERVER_PORT=!SCADA_MOBILE_BACKEND_PORT!" & set "SCADA_MOBILE_JWT_ACCESS_SECRET=!SCADA_MOBILE_JWT_ACCESS_SECRET!" & set "SCADA_MOBILE_JWT_REFRESH_SECRET=!SCADA_MOBILE_JWT_REFRESH_SECRET!" & $(GRADLEW) bootRun"

db-seed:
	cmd /V:ON /C "set \"SEED_DB_PASSWORD=$(SEED_DB_PASSWORD)\" & if not exist $(SEED_SQL) (echo Missing $(SEED_SQL). & exit /b 1) else if \"!SEED_DB_PASSWORD!\"==\"\" (echo Missing SEED_DB_PASSWORD. & exit /b 1) else (docker exec -i -e PGPASSWORD=!SEED_DB_PASSWORD! $(SEED_DB_CONTAINER_DEV) psql -U $(SEED_DB_USER) -d $(SEED_DB_NAME) -v ON_ERROR_STOP=1 -f - < $(SEED_SQL))"

db-seed-prod:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts\seed_prod.ps1 -EnvFile '$(PROD_ENV_ACTIVE_FILE)' -SeedSql '$(SEED_PROD_SQL)' -Container '$(SEED_DB_CONTAINER_PROD)'
else
back-run:
	@cd $(BACKEND_DIR) && \
	if [ -z "$(SCADA_MOBILE_JWT_ACCESS_SECRET)" ] || [ -z "$(SCADA_MOBILE_JWT_REFRESH_SECRET)" ]; then \
		if [ ! -f "$(DEV_SECRETS_FILE)" ]; then \
			echo "Generating dev JWT secrets into $(BACKEND_DIR)/$(DEV_SECRETS_FILE) (git-ignored)..."; \
			umask 077; \
			printf 'SCADA_MOBILE_JWT_ACCESS_SECRET=%s\n' "$$(openssl rand -base64 48 | tr -d '\n')" > "$(DEV_SECRETS_FILE)"; \
			printf 'SCADA_MOBILE_JWT_REFRESH_SECRET=%s\n' "$$(openssl rand -base64 48 | tr -d '\n')" >> "$(DEV_SECRETS_FILE)"; \
		fi; \
		set -a; . "./$(DEV_SECRETS_FILE)"; set +a; \
	else \
		SCADA_MOBILE_JWT_ACCESS_SECRET='$(SCADA_MOBILE_JWT_ACCESS_SECRET)'; \
		SCADA_MOBILE_JWT_REFRESH_SECRET='$(SCADA_MOBILE_JWT_REFRESH_SECRET)'; \
		export SCADA_MOBILE_JWT_ACCESS_SECRET SCADA_MOBILE_JWT_REFRESH_SECRET; \
	fi; \
	chmod +x ./gradlew; \
	JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=dev SERVER_PORT='$(DEV_BACKEND_PORT)' \
	nohup $(GRADLEW) bootRun > $(DEV_BACKEND_LOG) 2>&1 & echo $$! > .backend.pid
	@echo "Backend starting in background (log: $(BACKEND_DIR)/$(DEV_BACKEND_LOG)). Use 'make back-wait' to wait for readiness."

back-stop:
	@if [ -f "$(BACKEND_DIR)/.backend.pid" ]; then \
		kill $$(cat "$(BACKEND_DIR)/.backend.pid") 2>/dev/null || true; \
		rm -f "$(BACKEND_DIR)/.backend.pid"; \
	else \
		echo "No backend PID file found."; \
	fi
	@# gradlew/bootRun leaves a child JVM holding the port: finish off the listener
	@if [ -n "$(IS_MINGW)" ]; then \
		for pid in $$(netstat -ano | grep LISTENING | grep -E ':$(DEV_BACKEND_PORT)[[:space:]]' | awk '{print $$NF}' | sort -u); do \
			echo "Killing listener on port $(DEV_BACKEND_PORT) (PID $$pid)..."; \
			taskkill //F //PID $$pid > /dev/null 2>&1 || true; \
		done; \
	else \
		fuser -k $(DEV_BACKEND_PORT)/tcp 2>/dev/null || true; \
	fi

back-wait:
	@echo "Waiting for backend on port $(DEV_BACKEND_PORT)..."; \
	for i in $$(seq 1 60); do \
		code=$$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:$(DEV_BACKEND_PORT)$(API_BASE_PATH)/auth/login" -H 'Content-Type: application/json' -d '{}' 2>/dev/null); \
		if [ "$$code" != "000" ]; then \
			echo "backend is UP (HTTP $$code)"; \
			exit 0; \
		fi; \
		sleep 5; \
	done; \
	echo "backend did not respond in time"; \
	exit 1

back-logs:
	tail -n 200 -f "$(BACKEND_DIR)/$(DEV_BACKEND_LOG)"

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
	@docker exec -i -e PGPASSWORD="$(SEED_DB_PASSWORD)" "$(SEED_DB_CONTAINER_DEV)" \
		psql -U "$(SEED_DB_USER)" -d "$(SEED_DB_NAME)" -v ON_ERROR_STOP=1 -f - < "$(SEED_SQL)"

db-seed-prod:
	@if [ ! -f "$(SEED_PROD_SQL)" ]; then \
		echo "Missing $(SEED_PROD_SQL)."; \
		exit 1; \
	fi
	@if ! docker inspect --format='{{.State.Running}}' "$(SEED_DB_CONTAINER_PROD)" >/dev/null 2>&1; then \
		echo "Container $(SEED_DB_CONTAINER_PROD) is not running. Start it first: make docker-prod-up"; \
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
	docker exec -i -e PGPASSWORD="$$SCADA_MOBILE_DATABASE_PASSWORD" "$(SEED_DB_CONTAINER_PROD)" \
		psql -U "$$SCADA_MOBILE_POSTGRES_USER" -d "$$SCADA_MOBILE_POSTGRES_DB" -v ON_ERROR_STOP=1 -f - < "$(SEED_PROD_SQL)"
endif

ifeq ($(IS_POSIX),)
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

ifeq ($(IS_POSIX),)
docker-prod-up:
	@echo "Ошибка: запуск prod-стека на Windows не поддерживается."
	@echo "Используйте WSL2 (Ubuntu) или разворачивайте на Linux-сервере."
	@exit 1

docker-prod-down:
	cmd /C "docker compose --env-file $(PROD_ENV_ACTIVE_FILE) $(DOCKER_COMPOSE_FILE) down"

docker-ps:
	-cmd /C "docker compose --env-file $(PROD_ENV_ACTIVE_FILE) $(DOCKER_COMPOSE_FILE) ps"
else

docker-prod-up:
	@if [ -n "$(IS_MINGW)" ]; then \
		echo "Ошибка: запуск prod-стека на Windows не поддерживается."; \
		echo "Используйте WSL2 (Ubuntu) или разворачивайте на Linux-сервере."; \
		exit 1; \
	fi
	@if [ ! -f "$(PROD_ENV_FILE)" ]; then \
		echo "Missing $(PROD_ENV_FILE). Copy .env.prod.example -> $(PROD_ENV_FILE) and fill values."; \
		exit 1; \
	fi
	USER_ID=$$(id -u) GROUP_ID=$$(id -g) \
	docker-compose $(DOCKER_COMPOSE_FILE) --env-file "$(PROD_ENV_FILE)" up -d --build

docker-prod-down:
	docker compose --env-file "$(PROD_ENV_ACTIVE_FILE)" $(DOCKER_COMPOSE_FILE) down

docker-ps:
	-docker compose --env-file "$(PROD_ENV_ACTIVE_FILE)" $(DOCKER_COMPOSE_FILE) ps
endif

# ─────────────────────────────────────────────────────────────────────────────
# Нагрузочное тестирование (эпик #51): изолированный стенд.
# Только POSIX (Git Bash/WSL/Linux) — на нативном Windows используйте Git Bash.
# ─────────────────────────────────────────────────────────────────────────────
ifeq ($(IS_POSIX),)
load-up load-down load-db-up load-db-down load-db-reset load-db-seed load-db-backup load-db-restore \
load-back-run load-back-stop load-back-wait load-back-logs:
	@echo "Load-test targets поддерживаются только из POSIX-shell (Git Bash / WSL / Linux)."
	@exit 1
else

load-up: load-db-up
	@"$(MAKE)" load-back-run
	@"$(MAKE)" load-back-wait
	@"$(MAKE)" load-db-seed
	@echo "Restarting backend to pick up seeded topology..."
	@"$(MAKE)" load-back-stop
	@"$(MAKE)" load-back-run
	@"$(MAKE)" load-back-wait
	@echo ""
	@echo "Load-test stand is UP:"
	@echo "  backend:  http://localhost:$(LOAD_BACKEND_PORT) (profile: loadtest, mock PrintSrv)"
	@echo "  postgres: localhost:$(LOAD_DB_PORT) (container: $(LOAD_DB_CONTAINER))"
	@echo "  users:    20001..20500 / password"

load-down: load-back-stop
	@docker rm -f "$(LOAD_DB_CONTAINER)" > /dev/null 2>&1 \
		&& echo "$(LOAD_DB_CONTAINER) removed (volume $(LOAD_DB_VOLUME) kept)" \
		|| echo "$(LOAD_DB_CONTAINER) not found"

load-db-up:
	@if docker ps --format '{{.Names}}' | grep -qx "$(LOAD_DB_CONTAINER)"; then \
		echo "$(LOAD_DB_CONTAINER) already running"; \
	elif docker ps -a --format '{{.Names}}' | grep -qx "$(LOAD_DB_CONTAINER)"; then \
		docker start "$(LOAD_DB_CONTAINER)"; \
	else \
		docker run -d --name "$(LOAD_DB_CONTAINER)" \
			-p $(LOAD_DB_PORT):5432 \
			-e POSTGRES_DB="$(LOAD_DB_NAME)" \
			-e POSTGRES_USER="$(LOAD_DB_USER)" \
			-e POSTGRES_PASSWORD="$(LOAD_DB_PASSWORD)" \
			-v $(LOAD_DB_VOLUME):/var/lib/postgresql/data \
			postgres:17-alpine; \
	fi
	@echo "Waiting for postgres on port $(LOAD_DB_PORT)..."; \
	for i in $$(seq 1 30); do \
		if docker exec "$(LOAD_DB_CONTAINER)" pg_isready -U "$(LOAD_DB_USER)" -d "$(LOAD_DB_NAME)" > /dev/null 2>&1; then \
			echo "postgres is UP"; \
			exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "postgres did not become ready in time"; \
	exit 1

load-db-down:
	@docker rm -f "$(LOAD_DB_CONTAINER)" > /dev/null 2>&1 \
		&& echo "$(LOAD_DB_CONTAINER) removed" \
		|| echo "$(LOAD_DB_CONTAINER) not found"

load-db-reset: load-db-down
	@docker volume rm "$(LOAD_DB_VOLUME)" > /dev/null 2>&1 \
		&& echo "volume $(LOAD_DB_VOLUME) removed" \
		|| echo "volume $(LOAD_DB_VOLUME) not found"

load-db-seed:
	@for f in $(LOAD_SEED_SQL); do \
		if [ ! -f "$$f" ]; then \
			echo "Missing $$f."; \
			exit 1; \
		fi; \
	done
	@if ! docker ps --format '{{.Names}}' | grep -qx "$(LOAD_DB_CONTAINER)"; then \
		echo "Container $(LOAD_DB_CONTAINER) is not running. Start it first: make load-db-up"; \
		exit 1; \
	fi
	@for f in $(LOAD_SEED_SQL); do \
		echo "Applying $$f ..."; \
		docker exec -i -e PGPASSWORD="$(LOAD_DB_PASSWORD)" "$(LOAD_DB_CONTAINER)" \
			psql -U "$(LOAD_DB_USER)" -d "$(LOAD_DB_NAME)" -v ON_ERROR_STOP=1 -f - < "$$f" || exit 1; \
	done
	@echo -n "Users in stand DB: "
	@docker exec -e PGPASSWORD="$(LOAD_DB_PASSWORD)" "$(LOAD_DB_CONTAINER)" \
		psql -U "$(LOAD_DB_USER)" -d "$(LOAD_DB_NAME)" -tAc "SELECT count(*) FROM users"

load-db-backup:
	@mkdir -p "$(LOAD_BACKUP_DIR)"
	@if ! docker ps --format '{{.Names}}' | grep -qx "$(LOAD_DB_CONTAINER)"; then \
		echo "Container $(LOAD_DB_CONTAINER) is not running. Start it first: make load-db-up"; \
		exit 1; \
	fi
	@file="$(LOAD_BACKUP_DIR)/loadtest-$$(date +%Y%m%d-%H%M%S).sql"; \
	docker exec -e PGPASSWORD="$(LOAD_DB_PASSWORD)" "$(LOAD_DB_CONTAINER)" \
		pg_dump -U "$(LOAD_DB_USER)" -d "$(LOAD_DB_NAME)" --clean --if-exists > "$$file" \
		&& echo "Backup written: $$file"

load-db-restore:
	@if [ -z "$(FILE)" ]; then \
		echo "Usage: make load-db-restore FILE=$(LOAD_BACKUP_DIR)/loadtest-YYYYMMDD-HHMMSS.sql"; \
		exit 1; \
	fi
	@if [ ! -f "$(FILE)" ]; then \
		echo "Missing $(FILE)."; \
		exit 1; \
	fi
	@docker exec -i -e PGPASSWORD="$(LOAD_DB_PASSWORD)" "$(LOAD_DB_CONTAINER)" \
		psql -U "$(LOAD_DB_USER)" -d "$(LOAD_DB_NAME)" -v ON_ERROR_STOP=1 < "$(FILE)" \
		&& echo "Restored from $(FILE)"

load-back-run:
	@cd $(BACKEND_DIR) && \
	if [ -z "$(SCADA_MOBILE_JWT_ACCESS_SECRET)" ] || [ -z "$(SCADA_MOBILE_JWT_REFRESH_SECRET)" ]; then \
		if [ ! -f "$(DEV_SECRETS_FILE)" ]; then \
			echo "Generating dev JWT secrets into $(BACKEND_DIR)/$(DEV_SECRETS_FILE) (git-ignored)..."; \
			umask 077; \
			printf 'SCADA_MOBILE_JWT_ACCESS_SECRET=%s\n' "$$(openssl rand -base64 48 | tr -d '\n')" > "$(DEV_SECRETS_FILE)"; \
			printf 'SCADA_MOBILE_JWT_REFRESH_SECRET=%s\n' "$$(openssl rand -base64 48 | tr -d '\n')" >> "$(DEV_SECRETS_FILE)"; \
		fi; \
		set -a; . "./$(DEV_SECRETS_FILE)"; set +a; \
	fi; \
	chmod +x ./gradlew; \
	JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=loadtest SERVER_PORT='$(LOAD_BACKEND_PORT)' \
	SCADA_MOBILE_DATABASE_URL='jdbc:postgresql://localhost:$(LOAD_DB_PORT)/$(LOAD_DB_NAME)' \
	SCADA_MOBILE_DATABASE_USERNAME='$(LOAD_DB_USER)' \
	SCADA_MOBILE_DATABASE_PASSWORD='$(LOAD_DB_PASSWORD)' \
	nohup $(GRADLEW) bootRun > $(LOAD_BACKEND_LOG) 2>&1 & echo $$! > $(LOAD_BACKEND_PID)
	@echo "Backend (loadtest) starting in background on port $(LOAD_BACKEND_PORT) (log: $(BACKEND_DIR)/$(LOAD_BACKEND_LOG))."

load-back-stop:
	@if [ -f "$(BACKEND_DIR)/$(LOAD_BACKEND_PID)" ]; then \
		kill $$(cat "$(BACKEND_DIR)/$(LOAD_BACKEND_PID)") 2>/dev/null || true; \
		rm -f "$(BACKEND_DIR)/$(LOAD_BACKEND_PID)"; \
	else \
		echo "No loadtest backend PID file found."; \
	fi
	@# gradlew/bootRun leaves a child JVM holding the port: finish off the listener
	@if [ -n "$(IS_MINGW)" ]; then \
		for pid in $$(netstat -ano | grep LISTENING | grep -E ':$(LOAD_BACKEND_PORT)[[:space:]]' | awk '{print $$NF}' | sort -u); do \
			echo "Killing listener on port $(LOAD_BACKEND_PORT) (PID $$pid)..."; \
			taskkill //F //PID $$pid > /dev/null 2>&1 || true; \
		done; \
	else \
		fuser -k $(LOAD_BACKEND_PORT)/tcp 2>/dev/null || true; \
	fi

load-back-wait:
	@echo "Waiting for loadtest backend on port $(LOAD_BACKEND_PORT)..."; \
	for i in $$(seq 1 60); do \
		code=$$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:$(LOAD_BACKEND_PORT)$(API_BASE_PATH)/auth/login" -H 'Content-Type: application/json' -d '{}' 2>/dev/null); \
		if [ "$$code" != "000" ]; then \
			echo "loadtest backend is UP (HTTP $$code)"; \
			exit 0; \
		fi; \
		sleep 5; \
	done; \
	echo "loadtest backend did not respond in time"; \
	exit 1

load-back-logs:
	tail -n 200 -f "$(BACKEND_DIR)/$(LOAD_BACKEND_LOG)"
endif
