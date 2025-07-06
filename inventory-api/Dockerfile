# 1. Build stage
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# copy the fat JAR from the build stage
COPY --from=build /workspace/target/inventory-api-1.0.0.jar ./inventory-api.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/inventory-api.jar"]
