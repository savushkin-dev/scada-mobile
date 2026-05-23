---
name: remote-deploy-guardian
description: 'Remote server deployment workflow for the SCADA Mobile project via SSH alias scada_mobile. Use when: deploying to production, checking server/container status, viewing logs, or pulling updates on the remote server. Covers read-only diagnostics, git pull, and docker compose operations on the remote host. Strictly prohibits file modifications on the server.'
---

# Remote Deploy Guardian

## Purpose
- Enforce safe, read-only-by-default workflow on the remote production server.
- Prevent accidental file modifications, git mutations, or development activity on the server.
- Standardize deployment via `git pull` + docker compose restart.

## Server Connection
- **SSH alias:** `scada_mobile`
- **Project path:** `/scada/scada-mobile` (cd into this immediately after connecting)
- **OS:** Ubuntu (Linux), Docker Engine 29.x, Docker Compose v5.x
- **User:** `scadadev`
- **Git remote:** `https://github.com/savushkin-dev/scada-mobile.git`
- **Branch:** `main`

## Hard Constraints (NEVER Violate)

1. **Working directory lock:** Never leave `/scada/scada-mobile`. All commands must be relative to this path.
2. **No file modifications:** Never modify files on the server. Forbidden:
   - `git add`, `git commit`, `git push`, `git rebase`, `git reset`
   - Editors: `vim`, `nano`, `sed -i`, `echo > file`, `> file`, `>> file`
   - File operations: `rm`, `cp`, `mv`, `touch`, `chmod`, `chown`
   - System commands: `sudo`, `apt`, `systemctl`, `service`
3. **Git pull only exception:** `git pull` is the ONLY permitted write operation for code updates.
4. **Deployment is the only permitted state change:** `make docker-prod-up`, `make docker-prod-down`, `docker compose ...` commands that start/stop/restart containers.
5. **Pre-flight check:** Before executing ANY command on the server, verify it contains no write operators (redirections `>`, `>>`, `| tee`, in-place edits).

## Allowed Command Categories

### Read-only / Status Commands
| Category | Examples |
|----------|----------|
| Git status | `git status`, `git log`, `git log --oneline`, `git diff`, `git branch`, `git remote -v` |
| File listing | `ls`, `ls -la`, `find` (without `-exec` or output redirection) |
| File viewing | `cat`, `head`, `tail`, `grep`, `less` |
| System status | `df`, `free`, `uptime`, `ps`, `top` (read-only) |
| Docker status | `docker ps`, `docker compose ps`, `docker compose logs`, `docker stats` |

### Permitted Write Commands (Exceptions)
| Command | Purpose | When to use |
|---------|---------|-------------|
| `git pull` | Pull latest changes from origin/main | After local code is committed, pushed, and CI passes |
| `make docker-prod-up` | Build and start production stack | After `git pull` or when deploying |
| `make docker-prod-down` | Stop and remove production stack | When taking service down for maintenance |
| `make docker-ps` | Show container status | Any time for diagnostics |
| `docker compose ... logs ...` | View service logs | For debugging issues |

## Deployment Workflow

### Standard Deploy (Code Update)
```bash
# 1. LOCAL: edit code, build, test, commit, push
#    (done on local machine, NOT on server)

# 2. SERVER: pull latest changes
ssh scada_mobile "cd /scada/scada-mobile && git pull"

# 3. SERVER: check current stack status
ssh scada_mobile "cd /scada/scada-mobile && make docker-ps"

# 4. SERVER: restart stack with new code
ssh scada_mobile "cd /scada/scada-mobile && make docker-prod-down && make docker-prod-up"

# 5. SERVER: verify containers are healthy
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
| Backend | `scada-mobile-backend-1` | `scada-mobile/backend:0.1.0` | `8090:8080` | HTTP actuator |
| Frontend | `scada-mobile-frontend-1` | `scada-mobile/frontend:0.1.0` | `5500:8080` | HTTP nginx |

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
ssh scada_mobile "curl -s http://localhost:8090/api/v1.0.0/health/live"
```

### Database connectivity
```bash
ssh scada_mobile "docker exec scada-mobile-postgres pg_isready -U scada_user -d scada_mobile"
```

### View admin credentials (if first run)
```bash
ssh scada_mobile "docker exec scada-mobile-backend-1 cat /app/admin-credentials.txt"
```

## Forbidden Patterns (Auto-Reject)

If a command contains any of these, STOP and refuse:
- `>` or `>>` (output redirection to files)
- `sudo`
- `git add`, `git commit`, `git push`, `git rebase`, `git reset`
- `vim`, `nano`, `emacs`
- `sed -i`
- `rm `, `cp `, `mv ` (with file paths)
- `chmod`, `chown`
- `apt`, `yum`, `dpkg`
- `systemctl`, `service`
- `cd ..` or paths outside `/scada/scada-mobile`

## Emergency Contacts / Escalation

If server-level changes are needed (OS updates, Docker config, firewall, disk expansion):
- Escalate to system administrator
- Do NOT attempt system-level changes via SSH
