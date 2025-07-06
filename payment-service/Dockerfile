# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# Assuming parent POM is resolvable from Maven repository or not strictly needed for this module's direct build
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Adjust JAR name if artifactId/version differ from payment-service-1.0.0.jar
COPY --from=build /workspace/target/payment-service-1.0.0.jar ./payment-service.jar
EXPOSE 8083 # Default port for payment-service is 8083 as per application.yml
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XshowSettings:vm","-jar","/app/payment-service.jar"]
