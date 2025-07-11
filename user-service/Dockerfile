# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first for better caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jdk-alpine

LABEL maintainer="MySillyDreams Platform Team"
LABEL version="0.0.1"
LABEL description="User Service for MySillyDreams Platform"

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=builder /app/target/user-service-*.jar app.jar

# Ensure the appuser owns the application files
RUN chown appuser:appgroup app.jar

# Switch to the non-root user
USER appuser

# Expose the port
EXPOSE 8082

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8082/actuator/health || exit 1

# Entry point to run the application
# -Xms and -Xmx should be tuned based on expected load and available resources.
# -Djava.security.egd=file:/dev/./urandom is good for entropy in containerized environments.
# -XX:+UseContainerSupport helps JVM respect container memory limits.
# -Dspring.profiles.active will be set by Kubernetes or Docker environment variables.
ENTRYPOINT ["java", \
            "-Xms256m", \
            "-Xmx768m", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-XX:+UseContainerSupport", \
            "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:default}", \
            "-jar", \
            "app.jar"]

# Notes for hardening (as per guide):
# - For Distroless:
#   FROM gcr.io/distroless/java17-debian11
#   COPY --from=builder /app/target/user-service-*.jar /app/app.jar (assuming multi-stage build)
#   WORKDIR /app
#   USER 1000 # Or nonroot
#   ENTRYPOINT ["/app/app.jar"] # Distroless images have different entrypoint setup
# - Ensure image scanning (Trivy/Clair) is in CI/CD.
# - Kubernetes PodSecurityStandards (no privileged, read-only root FS, drop capabilities) applied via K8s manifests.
