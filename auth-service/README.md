# Auth Service (auth-service)

## 1. Overview

The Auth Service is a Spring Boot-based microservice responsible for handling user authentication, authorization, and related security concerns within the MySillyDreams Platform. It integrates with Keycloak as an Identity Provider (IdP) and issues its own service-specific JWTs for internal use after successful Keycloak authentication.

Key responsibilities include:
- User login via Keycloak.
  - **Admin MFA**: For users with `ROLE_ADMIN`, MFA (TOTP) is mandatory if enabled. Login requires username, password, and a One-Time Password (OTP).
- Service-specific JWT issuance & validation.
- Role-based access control checks (via Spring Security & Keycloak roles).
- Password rotation initiation and logging (admin-privileged).
- Publishing authentication events (e.g., password rotated) to Kafka.
- Admin MFA setup and verification endpoints.
- Internal endpoint for provisioning MFA for admin users.

## 2. Prerequisites for Local Development

- **Java JDK**: Version 17 or higher.
- **Apache Maven**: Version 3.6+ (for building the project).
- **Docker & Docker Compose**: For running Keycloak, PostgreSQL, and Kafka locally.
- **Keycloak Instance**: A running Keycloak instance.
    - Realm: `MySillyDreams-Realm` (or as configured, e.g., `AuthTestRealm` for tests).
    - Client: `auth-service-client` (or as configured, with service accounts enabled, confidential access type, and appropriate redirect URIs).
    - Users: Test users within the realm, including at least one with `ROLE_ADMIN`.
- **PostgreSQL Instance**: A running PostgreSQL server.
    - Database: `authdb` (or as configured, e.g., `test_auth_db` for tests).
    - User/Password: Credentials with access to the database.
- **Apache Kafka Instance**: A running Kafka broker.

Refer to `docker-compose.yml` (if provided at the project root) for an example setup of these dependencies.

## 3. Configuration

The service is configured primarily through `src/main/resources/application.yml`. Environment variables are used to override default values.

### Key Environment Variables & Application Properties:

**Database:**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`

**Keycloak:**
- `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID` (`keycloak.resource`), `KEYCLOAK_SECRET` (`keycloak.credentials.secret`), `KEYCLOAK_SSL_REQUIRED`

**JWT (Service-Specific):**
- `JWT_SECRET`, `JWT_EXPIRATION_MS`

**Kafka:**
- `KAFKA_BROKER`

**MFA & Internal Operations:**
- `app.simple-encryption.secret-key` (as `APP_SIMPLE_ENCRYPTION_SECRET_KEY` env var): **CRITICAL** secret key for encrypting TOTP secrets. Must be 16, 24, or 32 bytes long. **MUST be overridden in production with a strong, unique key.**
- `app.internal-api.secret-key` (as `APP_INTERNAL_API_SECRET_KEY` env var): Secret API key for accessing internal admin provisioning endpoints. **MUST be overridden in production.**
- `app.mfa.issuer-name` (default: `MySillyDreamsPlatform`): Issuer name displayed in authenticator apps.

**Spring Profiles:**
- `SPRING_PROFILES_ACTIVE`

### Important Production Settings:
- **Encryption Keys**: `APP_SIMPLE_ENCRYPTION_SECRET_KEY` and `JWT_SECRET` must be strong, unique, and managed securely (e.g., Vault, K8s secrets).
- **Internal API Key**: `APP_INTERNAL_API_SECRET_KEY` must be strong and managed securely.
- **Database Schema**: `spring.jpa.hibernate.ddl-auto` should be `validate` or `none`. Use Liquibase/Flyway for migrations.
- **CORS Configuration (`SecurityConfig.java`)**: `allowedOrigins` must be restricted.
- Other points from previous "Important Production Settings" section remain valid.

## 4. Building and Running Locally
(Sections 4.1, 4.2, 4.3 remain largely the same, ensure new env vars are considered)

### 4.1 Build
```bash
mvn clean package
```

### 4.2 Running with Maven
```bash
# Set necessary env vars including APP_SIMPLE_ENCRYPTION_SECRET_KEY, APP_INTERNAL_API_SECRET_KEY
mvn spring-boot:run
```

### 4.3 Running with Docker
```bash
# Example, include new env vars:
# -e APP_SIMPLE_ENCRYPTION_SECRET_KEY="your-strong-encryption-key-for-totp" \
# -e APP_INTERNAL_API_SECRET_KEY="your-strong-internal-api-key" \
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  # ... other existing env vars ...
  myregistry/auth-service:latest
```

## 5. Running Tests
(Remains the same)
```bash
mvn test
```

## 6. API Endpoints

Refer to the OpenAPI/Swagger documentation (`/swagger-ui.html`, `/v3/api-docs`).

### Public Authentication Endpoints (`/auth`):
- **`POST /login`**:
    - Description: Authenticates a user. For admins with MFA enabled, `otp` field is required in the request body.
    - Request Body: `LoginRequest` (`{ "username": "...", "password": "...", "otp": "..." (optional) }`)
- **`POST /refresh`**: (No change)
- **`GET /validate`**: (No change)
- **`POST /password-rotate`**: (No change in public signature, still admin-only)

### Admin MFA Management Endpoints (`/auth/admin/mfa` - Require ROLE_ADMIN):
- **`POST /setup`**:
    - Description: Generates a new TOTP secret and QR code data URI for the authenticated admin to set up MFA. MFA remains disabled until verified.
    - Response: `MfaSetupResponse` (`{ "rawSecret": "...", "qrCodeDataUri": "..." }`)
- **`POST /verify`**:
    - Description: Verifies the provided OTP and enables MFA for the authenticated admin.
    - Request Body: `{ "otp": "123456" }`
    - Response: Success or error message.

### Internal Admin Provisioning Endpoints (`/internal/auth` - Requires X-Internal-API-Key):
- **`POST /provision-mfa-setup`**:
    - Description: For internal systems. Provisions initial MFA setup data (secret, QR URI) for a specified admin user ID. MFA remains disabled.
    - Request Body: `{ "adminUserId": "uuid", "adminUsername": "username_for_qr_label" }`
    - Response: `MfaSetupResponse`.

## 7. Kafka Events Produced
(No changes to Kafka events from this service in this update.)

## 8. API Documentation (OpenAPI/Swagger)
(Remains the same, new endpoints will be included due to annotations.)

## 9. Deployment
(Remains largely the same, ensure new environment variables for secret keys are managed in production Kubernetes secrets.)

## 10. Security Hardening Notes
(Remains largely the same, with added emphasis on managing the new encryption and internal API keys.)
- **MFA for Admins**: Now a core feature. Ensure TOTP secrets are handled securely (encrypted at rest).
- **Internal API Key**: The `/internal` endpoints are protected by a shared secret key. This key must be strong and access to it highly restricted. Consider mTLS for these internal routes if possible for enhanced security.
```
