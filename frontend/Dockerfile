# syntax=docker/dockerfile:1.7

FROM node:22.17.1-alpine AS builder
WORKDIR /app

# Сначала dependency-слой для кэша npm.
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27.5-alpine AS runtime

WORKDIR /usr/share/nginx/html

COPY deploy/nginx.docker.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

USER 101:101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=5 \
  CMD wget -q -O- http://127.0.0.1:8080/healthz | grep -q "ok" || exit 1

CMD ["nginx", "-g", "daemon off;"]
