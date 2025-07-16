# Runtime image using pre-built JAR
# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jdk-alpine

LABEL maintainer="MySillyDreams Platform Team"
LABEL version="0.0.1"
LABEL description="Eureka Server for MySillyDreams Platform"

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set the working directory
WORKDIR /app

# Copy the pre-built JAR file
COPY target/eureka-server-*.jar app.jar

# Ensure the appuser owns the application files
RUN chown appuser:appgroup app.jar

# Switch to the non-root user
USER appuser

# Expose the port
EXPOSE 8761

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8761/actuator/health || exit 1

# Entry point to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
