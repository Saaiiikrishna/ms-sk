# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Copy parent pom if it's essential for the build and not resolved from a repo
# COPY ../pom.xml ./parent-pom.xml
# Ensure src is copied correctly relative to WORKDIR
COPY src ./src
# If parent POM is local and needed: RUN mvn clean package -DskipTests -f pom.xml
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Adjust JAR name if artifactId/version differ from inventory-core-1.0.0.jar
COPY --from=build /workspace/target/inventory-core-1.0.0.jar ./inventory-core.jar
EXPOSE 8080
# Standard entry point with container support. Consider memory limits e.g. -Xmx512m
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XshowSettings:vm","-jar","/app/inventory-core.jar"]
