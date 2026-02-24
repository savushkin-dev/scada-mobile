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
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.jetbrains:annotations:26.0.1")
    // springdoc 3.x — ветка для Spring Boot 4 / Spring Framework 7
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
