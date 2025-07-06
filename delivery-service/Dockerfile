# Use an official OpenJDK runtime as a parent image (Java 17 as per pom.xml)
FROM eclipse-temurin:17-jre-jammy

ARG APP_NAME=delivery-service
WORKDIR /app

LABEL maintainer="dev@mysillydreams.com"
LABEL application=${APP_NAME}

COPY target/${APP_NAME}-*.jar ${APP_NAME}.jar

EXPOSE 8080 # Default Spring Boot port, ensure it matches server.port if customized

# Environment variables - defaults, will be overridden by Kubernetes or docker run
ENV SPRING_PROFILES_ACTIVE="docker"
# JAVA_OPTS for memory should be set by K8s/Docker orchestration for production
# For local 'docker run', you can pass it: docker run -e JAVA_OPTS="-Xms256m -Xmx512m" ...
# ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "/app/delivery-service.jar"]
# To explicitly use JAVA_OPTS from ENV inside container for the java command:
# ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/delivery-service.jar"]
# Sticking to simpler form, assuming K8s will provide JVM opts.
