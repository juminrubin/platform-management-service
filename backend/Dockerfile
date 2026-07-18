# syntax=docker/dockerfile:1.7

# -----------------------------------------------------------------------------
# Stage 1: build the Spring Boot fat JAR with Maven
# -----------------------------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

# Cache dependencies first (layer reuse when only source changes)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests

COPY src ./src
RUN mvn -B -q package -DskipTests \
    && cp target/platform-management-service-*.jar /workspace/app.jar

# -----------------------------------------------------------------------------
# Stage 2: slim runtime image
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

LABEL org.opencontainers.image.title="Platform Management Service" \
      org.opencontainers.image.description="Platform Management Service — Participants, Service Offerings, Entitlements REST API" \
      org.opencontainers.image.source="https://github.com/juminrubin/platform-management-service"

# Non-root user (required by many AKS Pod Security policies)
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home /app --shell /sbin/nologin app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER 1001:1001

EXPOSE 8080

# JVM tuned for containers (memory is cgroup-aware on modern JDKs)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE="k8s" \
    SERVER_PORT=8080

# Use shell form only to expand JAVA_OPTS; exec ensures SIGTERM reaches the JVM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
