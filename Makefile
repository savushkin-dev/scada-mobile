SHELL := /bin/sh

BACKEND_DIR := backend
FRONTEND_DIR := frontend
JAVA_OPTS := -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8

ifeq ($(OS),Windows_NT)
GRADLEW := gradlew.bat
else
GRADLEW := ./gradlew
endif

.PHONY: help back-run back-run-prod front-install front-dev front-build bwa-init bwa-build-apk
.PHONY: docker-dev-up docker-dev-down docker-prod-up docker-prod-down docker-ps

DOCKER_BASE_FILES := -f docker-compose.yml
DOCKER_DEV_FILES := -f docker-compose.dev.yml
DOCKER_PROD_FILES := -f docker-compose.prod.yml
PROD_ENV_FILE ?= .env.prod.local

help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend:"
	@echo "  make back-run       - запустить backend [профиль dev, Swagger включён]"
	@echo "  make back-run-prod  - запустить backend [профиль prod, Swagger выключен]"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-dev-up    - поднять docker-стек в dev режиме"
	@echo "  make docker-dev-down  - остановить docker-стек dev"
	@echo "  make docker-prod-up   - поднять docker-стек в prod режиме (env: PROD_ENV_FILE=.env.prod.local)"
	@echo "  make docker-prod-down - остановить docker-стек prod"
	@echo "  make docker-ps        - статус контейнеров текущего стека"
	@echo ""
	@echo "Frontend (placeholders):"
	@echo "  make front-install - placeholder: install frontend deps"
	@echo "  make front-dev     - placeholder: run frontend dev server"
	@echo "  make front-build   - placeholder: build frontend"
	@echo ""
	@echo "Bubblewrap (placeholders):"
	@echo "  make bwa-init      - placeholder: bubblewrap init"
	@echo "  make bwa-build-apk - placeholder: bubblewrap build apk"

ifeq ($(OS),Windows_NT)
back-run:
	cmd /C "chcp 65001 >NUL & cd $(BACKEND_DIR) & set JAVA_TOOL_OPTIONS=$(JAVA_OPTS) & set SPRING_PROFILES_ACTIVE=dev & $(GRADLEW) bootRun"

back-run-prod:
	cmd /C "chcp 65001 >NUL & cd $(BACKEND_DIR) & set JAVA_TOOL_OPTIONS=$(JAVA_OPTS) & set SPRING_PROFILES_ACTIVE=prod & $(GRADLEW) bootRun"
else
back-run:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=dev $(GRADLEW) bootRun

back-run-prod:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_OPTS)' SPRING_PROFILES_ACTIVE=prod $(GRADLEW) bootRun
endif

front-install:
	@echo "[placeholder] Add your frontend install command here."
	@echo "Example: cd $(FRONTEND_DIR) && npm install"

front-dev:
	@echo "[placeholder] Add your frontend dev command here."
	@echo "Example: cd $(FRONTEND_DIR) && npm run dev"

front-build:
	@echo "[placeholder] Add your frontend build command here."
	@echo "Example: cd $(FRONTEND_DIR) && npm run build"

bwa-init:
	@echo "[placeholder] Add bubblewrap init command here."
	@echo "Example: cd $(FRONTEND_DIR) && npx @bubblewrap/cli init --manifest https://your-domain/manifest.webmanifest"

bwa-build-apk:
	@echo "[placeholder] Add bubblewrap APK build command here."
	@echo "Example: cd android && npx @bubblewrap/cli build"

docker-dev-up:
	docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) up --build -d

docker-dev-down:
	docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) down

docker-prod-up:
	@if [ ! -f "$(PROD_ENV_FILE)" ]; then \
		echo "Missing $(PROD_ENV_FILE). Copy .env.prod.example -> $(PROD_ENV_FILE) and fill values."; \
		exit 1; \
	fi
	docker compose --env-file $(PROD_ENV_FILE) $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) up --build -d

docker-prod-down:
	docker compose $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) down

docker-ps:
	docker compose $(DOCKER_BASE_FILES) $(DOCKER_DEV_FILES) ps || true
	docker compose $(DOCKER_BASE_FILES) $(DOCKER_PROD_FILES) ps || true