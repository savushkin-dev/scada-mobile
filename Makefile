SHELL := /bin/sh

BACKEND_DIR := backend
FRONTEND_DIR := frontend
JAVA_OPTS := -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8
DEV_BACKEND_PORT ?= 8080
DEV_FRONTEND_PORT ?= 5500

ifeq ($(OS),Windows_NT)
GRADLEW := gradlew.bat
else
GRADLEW := ./gradlew
endif

.PHONY: help back-run back-run-prod front-install front-dev front-build
.PHONY: bwa-init bwa-build-apk
.PHONY: docker-dev-up docker-dev-down docker-prod-up docker-prod-down docker-ps

DOCKER_BASE_FILES := -f docker-compose.yml
DOCKER_DEV_FILES := -f docker-compose.dev.yml
DOCKER_PROD_FILES := -f docker-compose.prod.yml
PROD_ENV_FILE ?= .env.prod.local
PROD_ENV_FALLBACK := .env.prod.example
PROD_ENV_ACTIVE_FILE = $(if $(wildcard $(PROD_ENV_FILE)),$(PROD_ENV_FILE),$(PROD_ENV_FALLBACK))

ifeq ($(OS),Windows_NT)
help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend:"
	@echo "  make back-run       - запустить backend [профиль dev, порт $(DEV_BACKEND_PORT), Swagger включён]"
	@echo "  make back-run-prod  - запустить backend [профиль prod, порт из BACKEND_PORT, Swagger выключен]"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-dev-up    - поднять docker-стек в dev режиме"
	@echo "  make docker-dev-down  - остановить docker-стек dev"
	@echo "  make docker-prod-up   - поднять docker-стек в prod режиме (env: PROD_ENV_FILE=.env.prod.local)"
	@echo "  make docker-prod-down - остановить docker-стек prod"
	@echo "  make docker-ps        - статус контейнеров текущего стека"
	@echo ""
	@echo "Frontend:"
	@echo "  make front-install - установить зависимости фронтенда"
	@echo "  make front-dev     - запустить dev-сервер фронтенда (порт $(DEV_FRONTEND_PORT), strict)"
	@echo "  make front-build   - собрать фронтенд для production"
	@echo ""
	@echo "Bubblewrap (Android):"
	@echo "  make bwa-init      - создать/переинициализировать TWA-проект в папке android"
	@echo "  make bwa-build-apk - собрать APK через bubblewrap"
else
help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend:"
	@echo "  make back-run       - запустить backend [профиль dev, порт $(DEV_BACKEND_PORT), Swagger включён]"
	@echo "  make back-run-prod  - запустить backend [профиль prod, порт из BACKEND_PORT, Swagger выключен]"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-dev-up    - поднять docker-стек в dev режиме"
	@echo "  make docker-dev-down  - остановить docker-стек dev"
	@echo "  make docker-prod-up   - поднять docker-стек в prod режиме (env: PROD_ENV_FILE=.env.prod.local)"
	@echo "  make docker-prod-down - остановить docker-стек prod"
	@echo "  make docker-ps        - статус контейнеров текущего стека"
	@echo ""
	@echo "Frontend:"
	@echo "  make front-install - установить зависимости фронтенда"
	@echo "  make front-dev     - запустить dev-сервер фронтенда (порт $(DEV_FRONTEND_PORT), strict)"
	@echo "  make front-build   - собрать фронтенд для production"
	@echo ""
	@echo "Bubblewrap (Android):"
	@echo "  make bwa-init      - создать/переинициализировать TWA-проект в папке android"
	@echo "  make bwa-build-apk - собрать APK через bubblewrap"
endif

ifeq ($(OS),Windows_NT)
back-run:
	cmd /C "chcp 65001 >NUL & cd $(BACKEND_DIR) & set "JAVA_TOOL_OPTIONS=$(JAVA_OPTS)" & set "SPRING_PROFILES_ACTIVE=dev" & set "SERVER_PORT=$(DEV_BACKEND_PORT)" & $(GRADLEW) bootRun"

back-run-prod:
	cmd /V:ON /C "chcp 65001 >NUL & setlocal EnableDelayedExpansion & set "ENV_FILE=$(PROD_ENV_ACTIVE_FILE)" & (for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!ENV_FILE!") do (if not "%%A"=="" set "%%A=%%B")) & if "!BACKEND_PORT!"=="" (echo Missing BACKEND_PORT in !ENV_FILE!. & exit /b 1) & cd $(BACKEND_DIR) & set "JAVA_TOOL_OPTIONS=$(JAVA_OPTS)" & set "SPRING_PROFILES_ACTIVE=prod" & set "SERVER_PORT=!BACKEND_PORT!" & $(GRADLEW) bootRun"
else
back-run:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=dev SERVER_PORT='$(DEV_BACKEND_PORT)' $(GRADLEW) bootRun

back-run-prod:
	@set -a; \
	. "$(PROD_ENV_ACTIVE_FILE)"; \
	set +a; \
	if [ -z "$$BACKEND_PORT" ]; then \
		echo "Missing BACKEND_PORT in $(PROD_ENV_ACTIVE_FILE)."; \
		exit 1; \
	fi; \
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=prod SERVER_PORT="$$BACKEND_PORT" $(GRADLEW) bootRun
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
docker-dev-up:
	cmd /C "set "DEV_BACKEND_PORT=$(DEV_BACKEND_PORT)" & set "DEV_FRONTEND_PORT=$(DEV_FRONTEND_PORT)" & docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) up --build -d"

docker-dev-down:
	cmd /C "set "DEV_BACKEND_PORT=$(DEV_BACKEND_PORT)" & set "DEV_FRONTEND_PORT=$(DEV_FRONTEND_PORT)" & docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) down"

docker-prod-up:
	cmd /C "if not exist $(PROD_ENV_FILE) (echo Missing $(PROD_ENV_FILE). Copy .env.prod.example -^> $(PROD_ENV_FILE) and fill values. & exit /b 1) else (docker compose --env-file $(PROD_ENV_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) up --build -d)"

docker-prod-down:
	cmd /C "docker compose --env-file $(PROD_ENV_ACTIVE_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) down"

docker-ps:
	-docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) ps
	-cmd /C "docker compose --env-file $(PROD_ENV_ACTIVE_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) ps"
else
docker-dev-up:
	DEV_BACKEND_PORT='$(DEV_BACKEND_PORT)' DEV_FRONTEND_PORT='$(DEV_FRONTEND_PORT)' docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) up --build -d

docker-dev-down:
	DEV_BACKEND_PORT='$(DEV_BACKEND_PORT)' DEV_FRONTEND_PORT='$(DEV_FRONTEND_PORT)' docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) down

docker-prod-up:
	@if [ ! -f "$(PROD_ENV_FILE)" ]; then \
		echo "Missing $(PROD_ENV_FILE). Copy .env.prod.example -> $(PROD_ENV_FILE) and fill values."; \
		exit 1; \
	fi
	docker compose --env-file "$(PROD_ENV_FILE)" $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) up --build -d

docker-prod-down:
	docker compose --env-file "$(PROD_ENV_ACTIVE_FILE)" $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) down

docker-ps:
	-docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) ps
	-docker compose --env-file "$(PROD_ENV_ACTIVE_FILE)" $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) ps
endif
