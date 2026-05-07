# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S pgforge && adduser -S pgforge -G pgforge
USER pgforge
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
