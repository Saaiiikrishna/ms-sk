# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre

LABEL maintainer="MySillyDreams Platform Team"
LABEL version="0.0.1"
LABEL description="Spring Boot Admin Server for MySillyDreams Platform"

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create a non-root user and group
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=builder /app/target/admin-server-*.jar app.jar

# Ensure the appuser owns the application files
RUN chown appuser:appgroup app.jar

# Switch to the non-root user
USER appuser

# Expose the port
EXPOSE 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail http://localhost:8080/actuator/health || exit 1

# Entry point to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
