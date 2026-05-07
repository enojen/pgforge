plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
}

group = "io.pgforge"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

springBoot {
    mainClass = "io.pgforge.PgforgeApplication"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.flyway)

    runtimeOnly(libs.flyway.database.postgresql)

    implementation(libs.micrometer.registry.prometheus)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

spotless {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "org", "com", "io.pgforge", "")
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
