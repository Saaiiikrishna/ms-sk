# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first for better caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jdk-alpine

LABEL maintainer="MySillyDreams Platform Team"
LABEL version="0.0.1"
LABEL description="Auth Service for MySillyDreams Platform"

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=builder /app/target/auth-service-*.jar app.jar

# Ensure the appuser owns the application files
RUN chown appuser:appgroup app.jar

# Switch to the non-root user
USER appuser

# Expose the port the application runs on (as defined in application.yml)
EXPOSE 8081

# Healthcheck (optional, but good for orchestrators like Kubernetes)
# Ensure Spring Boot Actuator is included and health endpoint is enabled
# Alpine base images (like eclipse-temurin) typically include wget.
# If not, RUN apk --no-cache add curl
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1
  # Alternative with curl: CMD curl --fail http://localhost:8080/actuator/health || exit 1

# Entry point to run the application
# -Xms and -Xmx should be tuned based on expected load and available resources.
# -Djava.security.egd=file:/dev/./urandom is good for entropy in containerized environments.
# -XX:+UseContainerSupport to make JVM respect container memory limits (enabled by default in newer JDKs)
# -XX:MaxRAMPercentage=80.0 for example, to use 80% of container memory for heap
ENTRYPOINT ["java", \
            "-Xms256m", \
            "-Xmx512m", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:default}", \
            "-jar", \
            "app.jar"]

# Example: To pass SPRING_PROFILES_ACTIVE during docker run:
# docker run -e SPRING_PROFILES_ACTIVE=kubernetes ...
# Or it will be set by Kubernetes environment variables.
