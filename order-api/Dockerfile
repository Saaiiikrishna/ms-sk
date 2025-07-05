# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jre-jammy

# Set the working directory in the container
WORKDIR /app

# Add maintainer label (optional)
LABEL maintainer="your-email@example.com"

# Arguments for user and group (optional, for running as non-root)
ARG APP_USER=appuser
ARG APP_GROUP=appgroup
ARG UID=1001
ARG GID=1001

# Create a non-root user and group
# RUN groupadd -g $GID $APP_GROUP && useradd -u $UID -g $APP_GROUP -m $APP_USER

# Copy the fat JAR file into the container
# Assumes the JAR is built by Maven/Gradle in the 'target' directory
# and its name is something like 'order-api-0.0.1-SNAPSHOT.jar'.
# Using a wildcard to copy the jar to avoid hardcoding the version.
COPY target/order-api-*.jar order-api.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Optional: Change ownership to non-root user
# RUN chown -R $APP_USER:$APP_GROUP /app
# USER $APP_USER

# Define environment variables that can be overridden at runtime
ENV SPRING_PROFILES_ACTIVE="docker"
ENV JAVA_OPTS="-Xms256m -Xmx512m"
# Example: ENV KAFKA_BROKER="kafka:9092" (can be set by Kubernetes)
# Example: ENV KEYCLOAK_URL="http://keycloak:8080" (can be set by Kubernetes)

# Run the JAR file
# The exec form is used so that the Java process becomes PID 1 and can receive signals correctly.
ENTRYPOINT ["java", "-jar", "/app/order-api.jar"]

# Alternative ENTRYPOINT with Java options from ENV:
# ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/order-api.jar"]
# If using the above, ensure JAVA_OPTS is set or defaults appropriately.
# The current simpler form is often sufficient. Spring Boot itself picks up SPRING_APPLICATION_JSON or command-line args for memory.
# However, explicitly setting -Xms and -Xmx via JAVA_OPTS is a common practice.
# Let's refine the entrypoint to use JAVA_OPTS
# ENTRYPOINT ["java", ${JAVA_OPTS}, "-jar", "/app/order-api.jar"] -> This syntax is not correct for array form.

# Correct way to use JAVA_OPTS with exec form:
# ENTRYPOINT exec java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -jar /app/order-api.jar
# This requires using "sh -c" as the wrapper:
# ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar /app/order-api.jar"]
# For simplicity and direct exec, the guide's version is:
# ENTRYPOINT ["java","-jar","/app/order-api.jar"]
# Spring Boot applications can also be configured via environment variables like SERVER_PORT, SPRING_PROFILES_ACTIVE etc.
# The JAVA_OPTS for memory can be passed during `docker run` or Kubernetes pod spec.
# The guide's ENTRYPOINT is the simplest and most direct.
# The eclipse-temurin image's default entrypoint might already handle some signal processing.
# The provided guide has: ENTRYPOINT ["java","-jar","/app/order-api.jar"]
# I will stick to the guide's version.
# Users can override JAVA_OPTS via Docker or K8s mechanisms.
# Spring Boot will pick up SPRING_PROFILES_ACTIVE from the ENV.
