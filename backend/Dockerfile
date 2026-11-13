# syntax=docker/dockerfile:1
# Refinr backend: Spring Boot on a Java 25 JRE, non-root (multi-arch: linux/amd64, linux/arm64).
# Configuration comes from environment variables only (see .env.example); no secret is baked into the image.

# --- Build: run on the build machine's own architecture (bytecode is architecture-independent) ---
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# The template reads every value from the environment: it becomes the image's application.properties.
# Tests run in CI (and locally with mvn test), not in the image build.
RUN cp src/main/resources/application-example.properties src/main/resources/application.properties
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/refinr-*.jar extract --layers --launcher --destination /build/extracted

# --- Runtime ---
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 refinr && adduser -S -u 10001 -G refinr -h /app refinr
WORKDIR /app
# Spring Boot layers, from the least to the most frequently changed (better image layer reuse)
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/v1/health/live || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
