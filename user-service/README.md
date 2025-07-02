# User Service (user-service)

## 1. Overview

The User Service is a Spring Boot-based microservice responsible for managing user profiles (including PII), vendor onboarding, inventory management, and administrative ("god mode") functionalities within the MySillyDreams Platform.

**Core User Functionality:**
- CRUD operations on user profiles.
- Field-level encryption for sensitive PII (e.g., name, email, phone, DOB) using HashiCorp Vault's Transit Secrets Engine.
- Management of user addresses, payment information (tokenized), and login sessions.
- User roles management (e.g., `ROLE_USER`, `ROLE_VENDOR_USER`, `ROLE_INVENTORY_USER`, `ROLE_ADMIN`).

**Vendor Onboarding Module:**
- Registration of new vendors, linking them to existing user accounts (assigns `ROLE_VENDOR_USER`).
- Management of vendor profiles and their KYC (Know Your Customer) status.
- Handling of KYC document uploads via S3 pre-signed URLs.
- Integration with a KYC orchestrator by publishing events to Apache Kafka.

**Inventory Management Module:**
- Onboarding of users as inventory managers (assigns `ROLE_INVENTORY_USER` and creates an `InventoryProfile`).
- CRUD operations for inventory items (SKU, name, description, quantity, reorder level).
- Stock adjustment functionalities (receive, issue, adjust) with transaction logging.
- Kafka event publishing for inventory item creation and stock adjustments.

**Admin ("God Mode") Functionality:**
- Dedicated `/admin/*` API endpoints for privileged operations, accessible only by users with `ROLE_ADMIN`.
- Ability for admins to list all users, vendor profiles, inventory profiles.
- Conceptual ability to manage (get, update, delete) any user or entity (further endpoint implementation needed).
- Secure internal endpoint for provisioning User-Service local profiles for admin users.
- Audit logging for actions performed by administrators.

## 2. Prerequisites for Local Development
(No changes from previous version of README)
- **Java JDK**: Version 17 or higher.
- **Apache Maven**: Version 3.6+ (for building the project).
- **Docker & Docker Compose**: For running HashiCorp Vault, PostgreSQL, Apache Kafka, and an S3-compatible service (e.g., LocalStack) locally.
- **HashiCorp Vault Instance**:
    - Transit Secrets Engine enabled.
    - A transit key named `user-service-key` (or as configured via `spring.cloud.vault.transit.default-key-name`).
    - AppRole authentication configured, with `VAULT_ROLE_ID` and `VAULT_SECRET_ID` available as environment variables.
    - KV Secrets Engine (v2 recommended, at path `secret/`) to store database credentials if not using dynamic DB secrets.
- **PostgreSQL Instance**:
    - Database created (e.g., `usersdb_default`).
- **Apache Kafka Instance**.
- **S3-Compatible Storage** (e.g., LocalStack, MinIO, or AWS S3 for testing against real S3):
    - Bucket created (e.g., `mysillydreams-vendor-docs`).

## 3. Configuration

The service is configured via `src/main/resources/bootstrap.yml` (for Vault bootstrap) and `src/main/resources/application.yml`. Environment variables override defaults.

### Key Environment Variables & Application Properties:
(Previous keys remain)
- `db.url`, `db.username`, `db.password`
- `KAFKA_BROKERS`
- `spring.cloud.vault.transit.default-key-name`
- `VENDOR_DOCS_S3_BUCKET`, `AWS_S3_REGION`, `AWS_S3_ENDPOINT_OVERRIDE`
- `KYC_TOPIC_START`, `KYC_TOPIC_DOCUMENT_UPLOADED`
- `INVENTORY_TOPIC_ITEM_CREATED`, `INVENTORY_TOPIC_STOCK_ADJUSTED`
- **New/Updated for Admin Features:**
  - `app.internal-api.secret-key` (as `APP_INTERNAL_API_SECRET_KEY_USER_SVC` env var, if different from Auth-Service): Secret API key for User-Service internal admin provisioning endpoints. **MUST be overridden in production.**

### Important Production Settings:
(Previous points remain valid)
- **Internal API Key**: `APP_INTERNAL_API_SECRET_KEY_USER_SVC` must be strong and managed securely.

## 4. Building and Running Locally
(No significant changes, ensure new env vars are considered if specific to User-Service internal API key)

## 5. Running Tests
(Remains the same)

## 6. API Endpoints

Refer to the OpenAPI/Swagger documentation (`/swagger-ui.html`, `/v3/api-docs`).

### Core User Endpoints (`/users`): (No change)
- **`POST /users`**: Create a new user profile.
- **`GET /users/{referenceId}`**: Get user profile by reference ID.
- **`PUT /users/{referenceId}`**: Update user profile.

### Vendor Onboarding Endpoints (`/vendor-onboarding`): (No change)
- **`POST /register`**: Register as a new vendor. Requires `X-User-Id` header.
- **`GET /profile`**: Get current vendor's profile. Requires `X-User-Id` header.
- **`POST /documents/upload-url`**: Generate a pre-signed URL for KYC document. Requires `X-User-Id` and `docType`.

### Inventory Management Endpoints (`/inventory-onboarding`, `/inventory`): (No change)
- **`POST /inventory-onboarding/register`**: Register user as inventory user. Requires `X-User-Id`.
- **`GET /inventory-onboarding/profile`**: Get inventory user's profile. Requires `X-User-Id`.
- **`POST /inventory/items`**: Add item. Requires `X-Inventory-Profile-Id`.
- **`GET /inventory/items`**: List items. Requires `X-Inventory-Profile-Id`.
- **`POST /inventory/items/{itemId}/adjust`**: Adjust stock.

### Admin Endpoints (`/admin` - Require ROLE_ADMIN):
- **`GET /users`**: List all user profiles (paginated).
- **`GET /users/{userId}`**: Get any user profile by UUID.
- **`DELETE /users/{userId}`**: (Conceptual) Delete any user profile.
- **`GET /vendor-profiles`**: List all vendor profiles (paginated).
- **`GET /inventory-profiles`**: List all inventory profiles (paginated).
  *(More admin endpoints to be added as needed for "god mode" operations).*

### Internal Admin Provisioning Endpoints (`/internal/users` - Requires X-Internal-API-Key):
- **`POST /provision-admin`**: For internal systems. Provisions a local User-Service profile for an existing Keycloak admin user, assigning `ROLE_ADMIN`.
    - Request Body: `{ "keycloakUserId": "uuid", "referenceId": "...", "name": "...", "email": "..." }`

## 7. Kafka Events
(No changes to listed Kafka events from this update. Audit logs are local to User-Service for now.)

## 8. Security & Hardening Notes
(Previous points remain valid)
- **Admin "God Mode"**: Access to `/admin/*` endpoints is strictly limited to users with `ROLE_ADMIN`. All actions taken through these endpoints should be heavily audited.
- **Admin Provisioning**: Creation of User-Service admin profiles is via a secured internal endpoint, assuming primary admin identity and `ROLE_ADMIN` assignment is managed in Keycloak.
- **Audit Logging**: Actions performed by administrators via `/admin/*` endpoints are logged with an "ADMIN_ACTION" tag (requires log configuration to route `AdminActionAuditLogger` to a secure audit trail).
```
