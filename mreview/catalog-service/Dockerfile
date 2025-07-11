# Stage 1: Build the application
FROM maven:3.8.5-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Create the runtime image
FROM openjdk:17-jdk-slim
WORKDIR /app
ARG JAR_FILE=/app/target/catalog-service-0.0.1-SNAPSHOT.jar
COPY --from=builder ${JAR_FILE} application.jar

# Expose the application port (make sure it matches server.port in application.yml)
EXPOSE 8082

# Environment variables (can be overridden at runtime)
ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE="docker"
    # Add other ENV vars like DB_URL, KAFKA_BROKERS, OPENSEARCH_URIS, REDIS_HOST if not using Spring Cloud Config

# Run the application
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
# Or with Java Opts:
# ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/application.jar"]
