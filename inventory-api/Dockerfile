# Use a specific OpenJDK version for better reproducibility
FROM openjdk:17.0.2-jdk-slim

# Set user and group to a non-root user for security
ARG APP_USER=appuser
ARG APP_GROUP=appgroup
RUN groupadd -r ${APP_GROUP} && useradd -r -g ${APP_GROUP} ${APP_USER}

# Set working directory
WORKDIR /app

# Argument for the JAR file name/path
ARG JAR_FILE=target/inventory-api-*-SNAPSHOT.jar

# Copy the JAR file from the build context to the image
# Ensure the JAR_FILE path matches what your Maven build produces.
# Using a wildcard for the version number in JAR_FILE if it changes.
COPY ${JAR_FILE} app.jar

# Change ownership of the app directory and jar file
RUN chown -R ${APP_USER}:${APP_GROUP} /app
USER ${APP_USER}

# Expose the port the application runs on (matches server.port in application.yml)
EXPOSE 8081

# Command to run the application
# Use exec form to make Java the main process (PID 1) for proper signal handling
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Optional: Add a healthcheck (requires actuator/health endpoint)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
#   CMD curl -f http://localhost:8081/actuator/health || exit 1
