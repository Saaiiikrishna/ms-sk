# Use a minimal base image like Eclipse Temurin (based on Alpine Linux) or Distroless
FROM eclipse-temurin:17-jdk-alpine

LABEL maintainer="MySillyDreams Platform Team"
LABEL version="0.0.1"
LABEL description="User Service for MySillyDreams Platform"

# Define an argument for the JAR file name/path from the build stage or local target
ARG JAR_FILE=target/user-service-*.jar

# Create a non-root user and group for security
# Using fixed UID/GID can be beneficial for K8s PodSecurityContext
ENV APP_USER_UID=1001
ENV APP_GROUP_GID=1001
RUN addgroup -g ${APP_GROUP_GID} -S appgroup && \
    adduser -u ${APP_USER_UID} -S appuser -G appgroup

# Set the working directory
WORKDIR /app

# Copy the application JAR file
COPY ${JAR_FILE} app.jar

# Ensure the appuser owns the application files
RUN chown appuser:appgroup app.jar

# Switch to the non-root user
USER appuser

# Expose the port the application runs on (default 8081 as per application.yml)
EXPOSE 8081

# Healthcheck using Spring Boot Actuator health endpoint
# wget is available in eclipse-temurin alpine images
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1

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
            "/app.jar"]

# Notes for hardening (as per guide):
# - For Distroless:
#   FROM gcr.io/distroless/java17-debian11
#   COPY --from=builder /app/target/user-service-*.jar /app/app.jar (assuming multi-stage build)
#   WORKDIR /app
#   USER 1000 # Or nonroot
#   ENTRYPOINT ["/app/app.jar"] # Distroless images have different entrypoint setup
# - Ensure image scanning (Trivy/Clair) is in CI/CD.
# - Kubernetes PodSecurityStandards (no privileged, read-only root FS, drop capabilities) applied via K8s manifests.
```
