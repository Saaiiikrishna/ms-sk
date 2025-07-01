# Auth Service (auth-service)

## 1. Overview

The Auth Service is a Spring Boot-based microservice responsible for handling user authentication, authorization, and related security concerns within the MySillyDreams Platform. It integrates with Keycloak as an Identity Provider (IdP) and issues its own service-specific JWTs for internal use after successful Keycloak authentication. It also manages password rotation logs and publishes relevant authentication events to Kafka.

Key responsibilities include:
- User login via Keycloak.
- Service-specific JWT issuance & validation.
- Role-based access control checks (via Spring Security & Keycloak roles).
- Password rotation initiation and logging.
- Publishing authentication events (e.g., password rotated) to Kafka.

## 2. Prerequisites for Local Development

- **Java JDK**: Version 17 or higher.
- **Apache Maven**: Version 3.6+ (for building the project).
- **Docker & Docker Compose**: For running Keycloak, PostgreSQL, and Kafka locally.
- **Keycloak Instance**: A running Keycloak instance.
    - Realm: `MySillyDreams-Realm` (or as configured)
    - Client: `auth-service-client` (or as configured, with service accounts enabled, confidential access type, and appropriate redirect URIs).
    - Users: Test users within the realm.
- **PostgreSQL Instance**: A running PostgreSQL server.
    - Database: `authdb` (or as configured)
    - User/Password: Credentials with access to the database.
- **Apache Kafka Instance**: A running Kafka broker.

Refer to `docker-compose.yml` (if provided at the project root) for an example setup of these dependencies.

## 3. Configuration

The service is configured primarily through `src/main/resources/application.yml`. Environment variables are used to override default values, especially for sensitive information and environment-specific settings.

### Key Environment Variables:

**Database:**
- `DB_HOST`: Hostname of the PostgreSQL server (default: `localhost`).
- `DB_PORT`: Port of the PostgreSQL server (default: `5432`).
- `DB_NAME`: Database name (default: `authdb`).
- `DB_USER`: Username for PostgreSQL (default: `authuser`).
- `DB_PASS`: Password for PostgreSQL (default: `authpassword`).

**Keycloak:**
- `KEYCLOAK_URL`: Base URL of the Keycloak auth server (e.g., `http://localhost:8080/auth`).
- `KEYCLOAK_REALM`: Keycloak realm name (default: `MySillyDreams-Realm`).
- `KEYCLOAK_CLIENT_ID`: Client ID for this service in Keycloak (default: `auth-service-client`).
- `KEYCLOAK_SECRET`: Client secret for this service in Keycloak. **MUST be set securely.**
- `KEYCLOAK_SSL_REQUIRED`: SSL requirement for Keycloak (e.g., `none` for local dev, `external` or `all` for prod).

**JWT (Service-Specific):**
- `JWT_SECRET`: Secret key for signing and verifying service-specific JWTs. **MUST be a strong, long, random string (at least 64 characters for HS512) and managed securely.**
- `JWT_EXPIRATION_MS`: Expiration time for service-specific JWTs in milliseconds (default: `3600000` - 1 hour).

**Kafka:**
- `KAFKA_BROKER`: Comma-separated list of Kafka broker addresses (e.g., `localhost:9092`).

**Spring Profiles:**
- `SPRING_PROFILES_ACTIVE`: Active Spring profiles (e.g., `dev`, `prod`, `kubernetes`).

### Important Production Settings:
- **`spring.jpa.hibernate.ddl-auto`**: Should be set to `validate` or `none` in production. Database schema migrations should be handled by tools like Liquibase or Flyway.
- **CORS Configuration (`SecurityConfig.java`)**: The `allowedOrigins` in CORS configuration must be restricted to your frontend application's domain(s) in production. Do not use `*`.
- **JWT Secret Management**: The `JWT_SECRET` must be managed securely (e.g., using HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, or GCP Secret Manager) and injected as an environment variable or via Kubernetes secrets. Do not hardcode production secrets.

## 4. Building and Running Locally

### 4.1 Build
To build the project and create the executable JAR:
```bash
mvn clean package
```
This will produce `target/auth-service-*.jar`.

### 4.2 Running with Maven
Ensure Keycloak, PostgreSQL, and Kafka are running and accessible. Configure the necessary environment variables (or update `application-dev.yml` if you create one).
```bash
# Example:
# export KEYCLOAK_URL=http://localhost:8080/auth
# export KEYCLOAK_SECRET=your-client-secret
# export JWT_SECRET=your-super-long-and-secure-jwt-secret
# ... other variables

mvn spring-boot:run
```
The service will typically start on port `8080`.

### 4.3 Running with Docker (after building JAR)
1.  Build the JAR: `mvn clean package`
2.  Build the Docker image: `docker build -t myregistry/auth-service:latest .`
3.  Run the Docker container (example, assuming dependencies are on `host.docker.internal` or a shared network):
    ```bash
    docker run -p 8080:8080 \
      -e SPRING_PROFILES_ACTIVE=dev \
      -e DB_HOST=host.docker.internal \
      -e KEYCLOAK_URL=http://host.docker.internal:8080/auth \
      -e KEYCLOAK_SECRET="your-client-secret" \
      -e JWT_SECRET="your-super-long-and-secure-jwt-secret" \
      -e KAFKA_BROKER=host.docker.internal:9092 \
      myregistry/auth-service:latest
    ```
    Using a `docker-compose.yml` file for managing the service and its dependencies (Keycloak, Postgres, Kafka) locally is highly recommended.

## 5. Running Tests

To run all unit and integration tests:
```bash
mvn test
```
Integration tests use Testcontainers to spin up Keycloak, PostgreSQL, and an embedded Kafka instance, so Docker must be running.

## 6. API Endpoints

The service exposes the following REST API endpoints. For detailed request/response formats, refer to the OpenAPI/Swagger documentation (see section 8).

- **`POST /auth/login`**:
    - Description: Authenticates a user with username and password against Keycloak. Returns a service-specific JWT upon success.
    - Request Body: `LoginRequest` (`{ "username": "...", "password": "..." }`)
    - Response: `JwtResponse` (`{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": ... }`)

- **`POST /auth/refresh`**:
    - Description: Refreshes a service-specific JWT. Expects the current valid JWT in the request body.
    - Request Body: `TokenRefreshRequest` (`{ "refreshToken": "current-jwt" }`)
    - Response: `JwtResponse` (new token)

- **`GET /auth/validate`**:
    - Description: Validates a service-specific JWT passed in the `Authorization: Bearer <token>` header.
    - Response: `{ "status": "valid/invalid", "user": "...", "authorities": [...] }` or error.

- **`POST /auth/password-rotate`**:
    - Description: Initiates a password rotation for a user (forces password update on next login). Requires ADMIN role.
    - Request Parameter: `userId` (UUID of the user)
    - Response: Success message or error.

## 7. Kafka Events Produced

- **Topic**: `auth.events` (configurable via `AuthEvents.AUTH_EVENTS_TOPIC`)
    - **Event Type (Key or Header)**: `auth.user.password_rotated` (as defined in `AuthEvents.PASSWORD_ROTATED`)
    - **Payload Example**:
      ```json
      {
        "userId": "uuid-of-the-user",
        "rotatedAt": "iso-8601-timestamp"
      }
      ```
    - **Description**: Published when a password rotation is successfully initiated for a user.

## 8. API Documentation (OpenAPI/Swagger)

If `springdoc-openapi-ui` is included, the OpenAPI specification and Swagger UI will be available:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec (JSON)**: `http://localhost:8080/v3/api-docs`

(This requires adding the dependency and annotations, which is the next step in the documentation plan).

## 9. Deployment

### 9.1 Docker Build & Push
1.  Build the application JAR: `mvn clean package`
2.  Build the Docker image: `docker build -t your-registry/auth-service:your-tag .`
3.  Push the image to your container registry: `docker push your-registry/auth-service:your-tag`

### 9.2 Kubernetes
The Kubernetes manifests are located in the `/k8s` directory:
- `secret.yaml`: Define sensitive configurations (DB credentials, Keycloak client secret, JWT secret). **Populate securely.**
- `configmap.yaml`: Define non-sensitive configurations (Keycloak URL, Kafka broker).
- `deployment.yaml`: Defines the deployment strategy, replicas, probes, resource requests/limits, etc. Update the image path to your pushed image.
- `service.yaml`: Exposes the deployment within the Kubernetes cluster.

Apply the manifests (ensure namespace exists or is created):
```bash
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## 10. Security Hardening Notes

- **Rate Limiting/Brute Force Protection**: Implement robust protection for the `/auth/login` endpoint, potentially at an API Gateway level or using libraries like Resilience4j/Bucket4j.
- **Dependency Vulnerability Scanning**: Integrate tools like OWASP Dependency-Check, Snyk, or GitHub Dependabot into your CI/CD pipeline to continuously scan for vulnerabilities in dependencies.
- **Static Application Security Testing (SAST)**: Use SAST tools (e.g., SonarQube, Checkmarx) to analyze code for security flaws.
- **Dynamic Application Security Testing (DAST)**: Consider DAST tools for testing the running application in a test environment.
- **Regular Security Audits**: Conduct periodic security audits and penetration tests.
- **Principle of Least Privilege**: Ensure the service account used for Keycloak Admin API operations has only the minimum necessary permissions. Similarly for the database user.
- **TLS Everywhere**: All external communication (to Keycloak, Kafka, clients) and ideally internal communication should be over TLS in production.
- **Secure Logging**: Avoid logging sensitive information. Use correlation IDs (MDC) for tracing requests.
- **Regular Updates**: Keep all dependencies, base Docker images, and Keycloak/PostgreSQL/Kafka versions patched and up-to-date.
```
