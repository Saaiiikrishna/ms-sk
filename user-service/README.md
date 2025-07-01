# User Service (user-service)

## 1. Overview

The User Service is a Spring Boot-based microservice responsible for managing user profiles, including sensitive Personal Identifiable Information (PII), and for handling vendor onboarding processes within the MySillyDreams Platform.

**Core User Functionality:**
- CRUD operations on user profiles.
- Field-level encryption for sensitive PII (e.g., name, email, phone, DOB) using HashiCorp Vault's Transit Secrets Engine.
- Management of user addresses, payment information (tokenized), and login sessions.

**Vendor Onboarding Module:**
- Registration of new vendors, linking them to existing user accounts.
- Management of vendor profiles and their KYC (Know Your Customer) status.
- Handling of KYC document uploads via S3 pre-signed URLs.
- Integration with a KYC orchestrator by publishing events to Apache Kafka.

## 2. Prerequisites for Local Development

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

Refer to a project-level `docker-compose.yml` (if available) for an example setup of these dependencies.

## 3. Configuration

The service is configured via `src/main/resources/bootstrap.yml` (for Vault bootstrap) and `src/main/resources/application.yml`. Environment variables override defaults.

### Key Environment Variables:

**Vault (`bootstrap.yml`):**
- `VAULT_HOST`, `VAULT_PORT`, `VAULT_SCHEME`
- `VAULT_AUTH_METHOD` (default: `APPROLE`)
- `VAULT_ROLE_ID`, `VAULT_SECRET_ID` (if using AppRole)
- `VAULT_TOKEN` (if using token auth for local dev)
- `VAULT_KV_BACKEND` (default: `secret`)

**Application (`application.yml` - many sourced from Vault):**
- `db.url`, `db.username`, `db.password` (JDBC URL, username, password for PostgreSQL - expected to be in Vault at `secret/data/user-service`).
- `KAFKA_BROKERS` (default: `localhost:9092`).
- `spring.cloud.vault.transit.default-key-name` (default: `user-service-key`).
- `VENDOR_DOCS_S3_BUCKET` (S3 bucket for vendor documents, default: `mysillydreams-vendor-docs`).
- `AWS_S3_REGION` (Optional: AWS region for S3, e.g., `us-east-1`).
- `AWS_S3_ENDPOINT_OVERRIDE` (Optional: For LocalStack/MinIO, e.g., `http://localhost:4566`).
- `KYC_TOPIC_START` (Kafka topic for starting KYC workflow, default: `kyc.vendor.start.v1`).
- `KYC_TOPIC_DOCUMENT_UPLOADED` (Kafka topic for document uploaded event, default: `kyc.vendor.document.uploaded.v1`).

### Important Production Settings:
- **Database Schema**: `spring.jpa.hibernate.ddl-auto` should be `validate` or `none`. Use Liquibase/Flyway for migrations.
- **Vault Scheme**: `VAULT_SCHEME` must be `https` for production.
- **Vault Authentication**: Use AppRole or Kubernetes Auth for production. Avoid static tokens.
- **S3 Bucket Policies & IAM**: Ensure S3 bucket has appropriate policies and the application uses IAM roles with least privilege for S3 access in production.
- **Kafka Security**: Enable SASL/SCRAM and TLS for Kafka in production. Configure ACLs.

## 4. Building and Running Locally

### 4.1 Build
```bash
mvn clean package
```
Produces `target/user-service-*.jar`.

### 4.2 Running (Example with environment variables)
Ensure Vault, PostgreSQL, Kafka, and S3 (LocalStack) are running.
```bash
# Set Vault related env vars (VAULT_HOST, VAULT_ROLE_ID, VAULT_SECRET_ID, etc.)
# Set KAFKA_BROKERS, VENDOR_DOCS_S3_BUCKET, AWS_S3_ENDPOINT_OVERRIDE (for LocalStack) etc.

# Example: Populate Vault with DB credentials (if not using dynamic secrets)
# vault kv put secret/user-service db.url="jdbc:postgresql://localhost:5432/usersdb_default" db.username="user_default" db.password="pass_default"

mvn spring-boot:run
```
Service typically starts on port `8081`.

## 5. Running Tests
```bash
mvn test
```
Integration tests require Docker to be running as they use Testcontainers for PostgreSQL, Vault, and LocalStack, plus an embedded Kafka.

## 6. API Endpoints

Refer to the OpenAPI/Swagger documentation for detailed request/response formats.
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **OpenAPI Spec (JSON)**: `http://localhost:8081/v3/api-docs`

### Core User Endpoints (`/users`):
- **`POST /users`**: Create a new user profile.
- **`GET /users/{referenceId}`**: Get user profile by reference ID.
- **`PUT /users/{referenceId}`**: Update user profile.

### Vendor Onboarding Endpoints (`/vendor-onboarding`):
- **`POST /register`**: Register as a new vendor. Requires `X-User-Id` header.
- **`GET /profile`**: Get current vendor's profile. Requires `X-User-Id` header.
- **`POST /documents/upload-url`**: Generate a pre-signed URL for uploading a KYC document. Requires `X-User-Id` header and `docType` query parameter.

## 7. Kafka Events

### Events Produced by User Service:
- **Topic**: `kyc.vendor.start.v1` (configurable via `kyc.topic.start`)
    - **Event Type**: `StartKycVendorWorkflow` (in payload)
    - **Key**: Workflow ID (UUID string)
    - **Payload Example**: `{"workflowId": "...", "vendorProfileId": "...", "eventType": "StartKycVendorWorkflow"}`
    - **Description**: Published by `KycOrchestratorClient` when a new vendor registration triggers a KYC workflow.

- **Topic**: `kyc.vendor.document.uploaded.v1` (configurable via `kyc.topic.documentUploaded`)
    - **Event Type**: `KycDocumentUploaded` (in payload)
    - **Key**: Document ID (UUID string)
    - **Payload Example**: `{"documentId": "...", "vendorProfileId": "...", "s3Key": "...", "docType": "...", "checksum": "...", "uploadedAt": "...", "eventType": "KycDocumentUploaded"}`
    - **Description**: Published by `DocumentService` after an S3 upload callback is processed.

*(TODO: Add `user.created`, `user.updated` events if/when implemented).*

## 8. Security & Hardening Notes

This service handles highly sensitive PII and vendor data. Refer to the "User-Service Hardening Guide" for comprehensive security measures. Key highlights implemented or to be strictly followed:
- **Field-Level Encryption**: Sensitive fields in `UserEntity`, `AddressEntity`, `PaymentInfoEntity` are encrypted using Vault Transit. `VendorProfile.legalName` encryption should be evaluated.
- **Secrets Management**: All secrets (DB credentials, Vault tokens, API keys) are managed via HashiCorp Vault.
- **Input Validation**: Applied on all DTOs and controller parameters.
- **Secure Dependencies**: Regularly scan dependencies (e.g., OWASP Dependency-Check, Snyk).
- **SAST/DAST**: Integrate into CI/CD.
- **Container Security**: Use minimal base images (Dockerfile provided), run as non-root, scan images (Trivy/Clair).
- **Infrastructure Hardening**: Enforce Pod Security Standards, NetworkPolicies, mTLS (Istio/Linkerd) in Kubernetes.
- **Kafka Security**: Use SASL/SCRAM, TLS, and ACLs for Kafka topics.
- **Database Security**: Use least-privilege roles (ideally dynamic from Vault), consider RLS, enable pgaudit.
- **Observability**: Implement structured JSON logging (avoiding PII), distributed tracing (OpenTelemetry), and metrics (Micrometer/Prometheus) with relevant alerts.
- **Compliance**: Adhere to GDPR, PCI-DSS (no raw card data), SOC 2, ISO 27001 controls as applicable. Implement data retention policies and right-to-be-forgotten mechanisms.
- **CI/CD Governance**: Secure build pipelines, image scanning, GitOps for deployments, PR-based approvals, secret scanning.

This README provides a starting point. More detailed operational runbooks and architectural diagrams should supplement this.
```
