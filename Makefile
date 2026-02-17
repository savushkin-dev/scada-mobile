SHELL := /bin/sh

BACKEND_DIR := backend
FRONTEND_DIR := frontend
BACKEND_SCRIPTS_DIR := $(BACKEND_DIR)/scripts
BACKEND_LOG_DIR := $(BACKEND_DIR)/logs
BACKEND_LOG_FILE := $(BACKEND_LOG_DIR)/backend.log
BACKEND_PID_FILE := $(BACKEND_LOG_DIR)/backend.pid
BACKEND_LOG_NAME := logs/backend.log
BACKEND_PID_NAME := logs/backend.pid
BACKEND_PORT ?= 8080
JAVA_UTF8_OPTS := -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8
JAVA_WIN1251_OPTS := -Dfile.encoding=windows-1251 -Dsun.stdout.encoding=windows-1251 -Dsun.stderr.encoding=windows-1251

ifeq ($(OS),Windows_NT)
GRADLEW := gradlew.bat
else
GRADLEW := ./gradlew
endif

.PHONY: help \
	back-run back-run-bg back-stop back-stop-force back-status back-build back-test back-clean \
	front-install front-dev front-build \
	bwa-init bwa-build-apk

help:
	@echo "SCADA Mobile shortcuts"
	@echo ""
	@echo "Backend (ready):"
	@echo "  make back-run      - run Spring Boot backend"
	@echo "  make back-run-bg   - run backend in background (silent, logs to file)"
	@echo "  make back-stop     - stop backend started in background"
	@echo "  make back-stop-force - emergency stop backend by port"
	@echo "  make back-status   - show backend background status"
	@echo "  make back-build    - build backend jar"
	@echo "  make back-test     - run backend tests"
	@echo "  make back-clean    - clean backend build"
	@echo "  log file: $(BACKEND_LOG_FILE)"
	@echo "  pid file: $(BACKEND_PID_FILE)"
	@echo "  backend port: $(BACKEND_PORT)"
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
	cmd /C "chcp 65001 >NUL & cd $(BACKEND_DIR) & set JAVA_TOOL_OPTIONS=$(JAVA_UTF8_OPTS) & $(GRADLEW) bootRun"

back-run-bg:
	powershell -NoProfile -ExecutionPolicy Bypass -File "$(BACKEND_SCRIPTS_DIR)/back-start-bg.ps1" -BackendDir "$(BACKEND_DIR)" -Port $(BACKEND_PORT) -JavaToolOptions "$(JAVA_UTF8_OPTS)"
	@echo "Logs: $(BACKEND_LOG_FILE)"
	@echo "PID file: $(BACKEND_PID_FILE)"

back-stop:
	powershell -NoProfile -ExecutionPolicy Bypass -File "$(BACKEND_SCRIPTS_DIR)/back-stop.ps1" -BackendDir "$(BACKEND_DIR)" -Port $(BACKEND_PORT)

back-stop-force:
	powershell -NoProfile -ExecutionPolicy Bypass -File "$(BACKEND_SCRIPTS_DIR)/back-stop-force.ps1" -BackendDir "$(BACKEND_DIR)" -Port $(BACKEND_PORT)

back-status:
	powershell -NoProfile -ExecutionPolicy Bypass -File "$(BACKEND_SCRIPTS_DIR)/back-status.ps1" -BackendDir "$(BACKEND_DIR)" -Port $(BACKEND_PORT)

back-build:
	cmd /C "chcp 1251 >NUL & cd $(BACKEND_DIR) & set JAVA_TOOL_OPTIONS=$(JAVA_WIN1251_OPTS) & $(GRADLEW) build"

back-test:
	cmd /C "chcp 1251 >NUL & cd $(BACKEND_DIR) & set JAVA_TOOL_OPTIONS=$(JAVA_WIN1251_OPTS) & $(GRADLEW) test"

back-clean:
	cmd /C "chcp 1251 >NUL & cd $(BACKEND_DIR) & set JAVA_TOOL_OPTIONS=$(JAVA_WIN1251_OPTS) & $(GRADLEW) clean"
else
back-run:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && JAVA_TOOL_OPTIONS='$(JAVA_UTF8_OPTS)' $(GRADLEW) bootRun

back-run-bg:
	@cd $(BACKEND_DIR) && mkdir -p logs && chmod +x ./gradlew && \
	if [ -f $(BACKEND_PID_NAME) ] && kill -0 $$(cat $(BACKEND_PID_NAME)) >/dev/null 2>&1; then \
		echo "Backend already running. PID=$$(cat $(BACKEND_PID_NAME))"; \
	else \
		nohup env JAVA_TOOL_OPTIONS='$(JAVA_UTF8_OPTS)' $(GRADLEW) bootRun >> $(BACKEND_LOG_NAME) 2>&1 & echo $$! > $(BACKEND_PID_NAME); \
		echo "Backend started in background. Logs: $(BACKEND_LOG_FILE)"; \
		echo "PID saved to: $(BACKEND_PID_FILE)"; \
	fi

back-stop:
	@cd $(BACKEND_DIR) && \
	killed=0; \
	if [ -f $(BACKEND_PID_NAME) ]; then \
		pid=$$(cat $(BACKEND_PID_NAME)); \
		if kill -0 $$pid >/dev/null 2>&1; then \
			kill $$pid >/dev/null 2>&1 || true; \
			sleep 1; \
			kill -9 $$pid >/dev/null 2>&1 || true; \
			killed=1; \
		fi; \
		rm -f $(BACKEND_PID_NAME); \
	fi; \
	if [ $$killed -eq 1 ]; then \
		echo "Backend stopped."; \
	else \
		echo "Backend process not found (or stale PID file). Use back-stop-force for port-based emergency stop."; \
	fi

back-stop-force:
	@cd $(BACKEND_DIR) && \
	pids=""; \
	if command -v ss >/dev/null 2>&1; then \
		pids=$$(ss -ltnp "sport = :$(BACKEND_PORT)" 2>/dev/null | awk -F'pid=' 'NR>1{split($$2,a,","); if(a[1] ~ /^[0-9]+$$/) print a[1]}' | sort -u); \
	fi; \
	if [ -z "$$pids" ] && command -v lsof >/dev/null 2>&1; then \
		pids=$$(lsof -ti tcp:$(BACKEND_PORT) -sTCP:LISTEN 2>/dev/null | sort -u); \
	fi; \
	if [ -z "$$pids" ] && command -v netstat >/dev/null 2>&1; then \
		pids=$$(netstat -lntp 2>/dev/null | awk '$$4 ~ /:$(BACKEND_PORT)$$/ {split($$7,a,"/"); if(a[1] ~ /^[0-9]+$$/) print a[1]}' | sort -u); \
	fi; \
	if [ -n "$$pids" ]; then \
		for pid in $$pids; do kill -9 $$pid >/dev/null 2>&1 || true; done; \
		rm -f $(BACKEND_PID_NAME); \
		echo "Emergency stop executed for port $(BACKEND_PORT)."; \
	else \
		echo "No process found on port $(BACKEND_PORT)."; \
	fi

back-status:
	@cd $(BACKEND_DIR) && \
	if [ -f $(BACKEND_PID_NAME) ] && kill -0 $$(cat $(BACKEND_PID_NAME)) >/dev/null 2>&1; then \
		echo "Backend is running. PID=$$(cat $(BACKEND_PID_NAME)), port=$(BACKEND_PORT)"; \
	else \
		pid=""; \
		if command -v ss >/dev/null 2>&1; then \
			pid=$$(ss -ltnp "sport = :$(BACKEND_PORT)" 2>/dev/null | awk -F'pid=' 'NR>1{split($$2,a,","); if(a[1] ~ /^[0-9]+$$/) {print a[1]; exit}}'); \
		fi; \
		if [ -z "$$pid" ] && command -v lsof >/dev/null 2>&1; then \
			pid=$$(lsof -ti tcp:$(BACKEND_PORT) -sTCP:LISTEN 2>/dev/null | head -n 1); \
		fi; \
		if [ -z "$$pid" ] && command -v netstat >/dev/null 2>&1; then \
			pid=$$(netstat -lntp 2>/dev/null | awk '$$4 ~ /:$(BACKEND_PORT)$$/ {split($$7,a,"/"); if(a[1] ~ /^[0-9]+$$/) {print a[1]; exit}}'); \
		fi; \
		if [ -n "$$pid" ]; then \
			echo $$pid > $(BACKEND_PID_NAME); \
			echo "Backend is running. PID=$$pid, port=$(BACKEND_PORT)"; \
		else \
			echo "Backend is not running."; \
		fi; \
	fi

back-build:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && $(GRADLEW) build

back-test:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && $(GRADLEW) test

back-clean:
	cd $(BACKEND_DIR) && chmod +x ./gradlew && $(GRADLEW) clean
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