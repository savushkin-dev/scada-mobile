plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.savushkin"
version = "0.0.1-SNAPSHOT"
description = "Backend service for SCADA Mobile system"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Явно подключаем Mockito как Java Agent для тестов.
// Это убирает предупреждения про self-attaching / dynamic agent loading
// (актуально на JDK 21+ и особенно для будущих релизов).
val mockitoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // на всякий случай: не даём тащить транзитивные зависимости в агент-конфигурацию
    isTransitive = false
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.jetbrains:annotations:26.0.1")
    // springdoc 3.x — ветка для Spring Boot 4 / Spring Framework 7
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    // Web Push Protocol (VAPID) — отправка push-уведомлений через браузерные push-сервисы.
    // nl.martijndwars:web-push реализует RFC 8030 + RFC 8292 (VAPID) поверх Apache HttpClient.
    implementation("nl.martijndwars:web-push:5.1.2")
    // Bouncy Castle — криптопровайдер для ECDH/ECDSA операций внутри web-push.
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    // Apache HttpCore — явно объявляем как compile-зависимость, так как web-push объявляет
    // httpasyncclient (и httpcore транзитивно) с runtime-скоупом, из-за чего HttpResponse
    // недоступен при компиляции нашего кода.
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    // jose4j — явно объявляем как compile-зависимость: web-push объявляет его runtime-скоупом,
    // но JoseException входит в throws-сигнатуру PushService.send() и нужен при компиляции.
    // Версия 0.9.6: закрывает CVE (DoS через сжатый JWE-контент) и слабые алгоритмы (<0.9.3).
    // Gradle выбирает эту версию вместо 0.7.9, транзитивно подтягиваемой через web-push.
    implementation("org.bitbucket.b_c:jose4j:0.9.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito Agent jar (используем ту же версию, что резолвится для тестов через BOM Spring).
    // Если понадобится зафиксировать версию — можно указать :mockito-core:<version>.
    mockitoAgent("org.mockito:mockito-core")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Важно: mockitoAgent.singleFile резолвится в конкретный jar в Gradle кеше
    // и корректно работает и на Windows.
    jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")
}

