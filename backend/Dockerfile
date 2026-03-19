# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21.0.8_9-jdk-alpine AS builder
WORKDIR /workspace

# Сначала копируем только build-метаданные для оптимального кэша зависимостей.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./

RUN chmod +x ./gradlew && ./gradlew --no-daemon help

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21.0.8_9-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app \
    && apk add --no-cache wget

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/logs && chown -R app:app /app

USER app

ENV SPRING_PROFILES_ACTIVE=dev
ENV JAVA_OPTS=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -q -O- http://127.0.0.1:8080/api/v1.0.0/health/live | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
