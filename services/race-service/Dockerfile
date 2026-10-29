# syntax=docker/dockerfile:1
# Build context is the repo root (see infra/docker-compose.yml), because the build also needs
# shared/ghost-format (settings.gradle: includeBuild '../../shared/ghost-format').

# Stage 1: build the jar with the Gradle wrapper. This JDK image is thrown away afterwards.
FROM eclipse-temurin:21-jdk AS build
# Same folder layout as the repo, so the relative includeBuild path still works.
COPY shared/ghost-format/ /workspace/shared/ghost-format/
COPY services/race-service/ /workspace/services/race-service/
WORKDIR /workspace/services/race-service
# The cache mount keeps Gradle and its downloads between builds, so rebuilds are quick.
# sharing=locked: compose builds both services at once, and they must not share the cache at the same time.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew bootJar --no-daemon

# Stage 2: only the jar, on a smaller JRE-only image, running as a non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /workspace/services/race-service/build/libs/app.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
