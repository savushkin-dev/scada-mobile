---
name: remote-deploy-guardian
description: 'Remote server deployment workflow for the SCADA Mobile project via SSH alias scada_mobile. Use when: deploying to production, checking server/container status, viewing logs, or pulling updates on the remote server. Covers read-only diagnostics, git pull, docker compose operations, and SCADA_MOBILE_ env var management on the remote host.'
---

# Remote Deploy Guardian

## Purpose
- Enforce safe, read-only-by-default workflow on the remote production server.
- Prevent accidental file modifications, git mutations, or development activity on the server.
- Standardize deployment via `git pull` + docker compose restart.
- Allow managing project-specific environment variables (`SCADA_MOBILE_*`) in `.env.prod.local`.

## Server Connection
- **SSH alias:** `scada_mobile`
- **Project path:** `/scada/scada-mobile` (cd into this immediately after connecting)
- **OS:** Ubuntu (Linux), Docker Engine 29.x, Docker Compose v5.x
- **User:** `scadadev`
- **Git remote:** `https://github.com/savushkin-dev/scada-mobile.git`
- **Branch:** `main`

## Hard Constraints (NEVER Violate)

1. **Working directory lock:** Never leave `/scada/scada-mobile`. All commands must be relative to this path.
2. **No file modifications except `.env.prod.local`:** Never modify source files, configs, or other files on the server. Forbidden:
   - `git add`, `git commit`, `git push`, `git rebase`, `git reset`
   - Editors: `vim`, `nano`, `sed -i` (on source files), `echo > file`, `> file`, `>> file` (on source files)
   - File operations: `rm`, `cp`, `mv`, `touch`, `chmod`, `chown` (on source files)
   - System commands: `sudo`, `apt`, `systemctl`, `service`
3. **Git pull only exception for code:** `git pull` is the ONLY permitted write operation for code updates.
4. **Deployment is the only permitted state change:** `make docker-prod-up`, `make docker-prod-down`, `docker compose ...` commands that start/stop/restart containers.
5. **Pre-flight check:** Before executing ANY command on the server, verify it contains no forbidden operators for non-env files.

## Environment Variable Management

### Scope
- Agent MAY read and modify **only** environment variables with the `SCADA_MOBILE_` prefix.
- These variables are stored in `.env.prod.local` in the project root (`/scada/scada-mobile/.env.prod.local`).
- This file is the single source of truth for the Docker Compose stack.

### Permitted Operations on `.env.prod.local`
| Operation | Command Example | Purpose |
|-----------|-----------------|---------|
| Read | `cat .env.prod.local \| grep SCADA_MOBILE_` | Check current values |
| Read specific | `grep SCADA_MOBILE_DATABASE_PASSWORD .env.prod.local` | Check one variable |
| Set / Update | `sed -i 's/^SCADA_MOBILE_XXX=.*$/SCADA_MOBILE_XXX=new_value/' .env.prod.local` | Update existing var |
| Add new | `echo 'SCADA_MOBILE_XXX=new_value' >> .env.prod.local` | Append new var |

### Critical Variables to Verify Before Deploy
Before running `make docker-prod-up`, ensure these are set in `.env.prod.local`:
- `SCADA_MOBILE_DATABASE_PASSWORD` — required, must be non-empty
- `SCADA_MOBILE_JWT_ACCESS_SECRET` — required, must be non-empty
- `SCADA_MOBILE_JWT_REFRESH_SECRET` — required, must be non-empty
- `SCADA_MOBILE_BACKEND_PORT` — required
- `SCADA_MOBILE_FRONTEND_PORT` — required
- `SCADA_MOBILE_CORS_POLICY_ALLOWED_ORIGINS` — required


## Deployment Workflow

### Pre-Deploy Checklist
1. Read `RUN_PROJECT_DOCKER.md` in project root for current deployment instructions.
2. Verify all required `SCADA_MOBILE_*` variables are set in `.env.prod.local`.
3. Ensure local code is committed and pushed to `origin/main`.

### Standard Deploy (Code Update)
```bash
# 1. LOCAL: edit code, build, test, commit, push
#    (done on local machine, NOT on server)

# 2. SERVER: verify env vars are set
ssh scada_mobile "cd /scada/scada-mobile && grep -E '^SCADA_MOBILE_(DATABASE_PASSWORD|JWT_ACCESS_SECRET|JWT_REFRESH_SECRET|BACKEND_PORT|FRONTEND_PORT|CORS_POLICY_ALLOWED_ORIGINS)=' .env.prod.local"

# 3. SERVER: pull latest changes
ssh scada_mobile "cd /scada/scada-mobile && git pull"

# 4. SERVER: check current stack status
ssh scada_mobile "cd /scada/scada-mobile && make docker-ps"

# 5. SERVER: restart stack with new code
ssh scada_mobile "cd /scada/scada-mobile && make docker-prod-down && make docker-prod-up"

# 6. SERVER: verify containers are healthy
ssh scada_mobile "cd /scada/scada-mobile && make docker-ps"
```

### Status Check (No Deploy)
```bash
# Container status
ssh scada_mobile "cd /scada/scada-mobile && make docker-ps"

# Recent logs (last 50 lines)
ssh scada_mobile "cd /scada/scada-mobile && docker compose --env-file .env.prod.local -f docker-compose.yml -f docker-compose.prod.yml logs --tail 50 backend"

# Git status on server
ssh scada_mobile "cd /scada/scada-mobile && git status && git log --oneline -3"
```

## Docker Stack Details

### Compose Files
- Base: `docker-compose.yml`
- Prod overlay: `docker-compose.prod.yml`
- Env file: `.env.prod.local`

### Services
| Service | Container Name | Image | Port (host) | Healthcheck |
|---------|---------------|-------|-------------|-------------|
| PostgreSQL | `scada-mobile-postgres` | `postgres:17-alpine` | `5433:5432` | `pg_isready` |
| Backend | `scada-mobile-backend-1` | `scada-mobile/backend:0.1.0` | (see `.env.prod.local`) | HTTP actuator |
| Frontend | `scada-mobile-frontend-1` | `scada-mobile/frontend:0.1.0` | (see `.env.prod.local`) | HTTP nginx |

### Data Persistence
- PostgreSQL data persists in named volume `scada-mobile-postgres-data`
- Data survives `docker-prod-down` and container recreation

## Quick Diagnostics

### Check if stack is running
```bash
ssh scada_mobile "cd /scada/scada-mobile && make docker-ps"
```

### Backend health endpoint
```bash
# Port depends on SCADA_MOBILE_BACKEND_PORT in .env.prod.local
ssh scada_mobile "curl -s http://localhost:<BACKEND_PORT>/actuator/health"
```

### Database connectivity
```bash
ssh scada_mobile "docker exec scada-mobile-postgres pg_isready -U scada_user -d scada_mobile"
```

### View admin credentials
```bash
# See RUN_PROJECT_DOCKER.md for current admin credentials location
ssh scada_mobile "cd /scada/scada-mobile && cat RUN_PROJECT_DOCKER.md | grep -A 20 'administrator'"
```

## Reference Documentation

- **Primary deploy guide:** `RUN_PROJECT_DOCKER.md` in project root
- **Local dev guide:** `MAKEFILE.md` in project root
- **Backend architecture:** `BACKEND_ARCHITECTURE.md` in project root

Always follow the instructions in `RUN_PROJECT_DOCKER.md` as the authoritative source for deployment steps.

## Forbidden Patterns (Auto-Reject)

If a command contains any of these, STOP and refuse:
- `>` or `>>` (output redirection to source/config files)
- `sudo`
- `git add`, `git commit`, `git push`, `git rebase`, `git reset`
- `vim`, `nano`, `emacs`
- `sed -i` (on source files)
- `rm `, `cp `, `mv ` (on source files)
- `chmod`, `chown` (on source files)
- `apt`, `yum`, `dpkg`
- `systemctl`, `service`
- `cd ..` or paths outside `/scada/scada-mobile`

## Emergency Contacts / Escalation

If server-level changes are needed (OS updates, Docker config, firewall, disk expansion):
- Escalate to system administrator
- Do NOT attempt system-level changes via SSH
