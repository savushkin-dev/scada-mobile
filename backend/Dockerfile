# syntax=docker/dockerfile:1.7

# Этап 1: Сборка
FROM gradle:8.14.3-jdk21-alpine AS builder
WORKDIR /workspace

COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon help

COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon --no-build-cache clean bootJar -x test

# Этап 2: Runtime
FROM eclipse-temurin:21.0.8_9-jre-alpine AS runtime

ARG USER_ID
ARG GROUP_ID

RUN apk add --no-cache wget su-exec

# Создаём пользователя ТОЛЬКО если не root (UID != 0)
RUN if [ "${USER_ID}" != "0" ]; then \
        addgroup -S -g ${GROUP_ID} appgroup && \
        adduser -S -u ${USER_ID} -G appgroup appuser; \
    fi

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

RUN mkdir -p /app/logs

ENTRYPOINT ["/entrypoint.sh"]

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1