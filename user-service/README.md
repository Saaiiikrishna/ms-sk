# User Service (user-service)

## 1. Overview

The User Service is a Spring Boot-based microservice responsible for managing user profiles (including PII), vendor onboarding, inventory management, delivery user operations, support ticketing, and administrative ("god mode") functionalities within the MySillyDreams Platform.

**Core User Functionality:**
- CRUD operations on user profiles.
- Field-level encryption for sensitive PII (e.g., name, email, phone, DOB) using HashiCorp Vault's Transit Secrets Engine.
- Management of user addresses, payment information (tokenized), and login sessions.
- User roles management (e.g., `ROLE_USER`, `ROLE_VENDOR_USER`, `ROLE_INVENTORY_USER`, `ROLE_DELIVERY_USER`, `ROLE_SUPPORT_USER`, `ROLE_ADMIN`).

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

**Delivery-User Module:**
- Onboarding of users as delivery personnel (assigns `ROLE_DELIVERY_USER` and creates `DeliveryProfile`).
- Management of order assignments for delivery users.
- Tracking delivery lifecycle events (arrival, photo upload, OTP verification, completion).
- S3 integration for storing delivery photos.
- Kafka event publishing for order assignments and delivery status changes.

**Support-User Module:**
- Onboarding of users as support agents (assigns `ROLE_SUPPORT_USER` and creates `SupportProfile`).
- Management of customer support tickets (creation, status updates, assignment).
- Handling of messages within support tickets, including optional attachment metadata.
- Kafka event publishing for ticket creation and updates.

**Admin ("God Mode") Functionality:**
- Dedicated `/admin/*` API endpoints for privileged operations, accessible only by users with `ROLE_ADMIN`.
- Ability for admins to list all users, vendor profiles, inventory profiles, etc.
- Secure internal endpoint for provisioning User-Service local profiles for admin users.
- Audit logging for actions performed by administrators.

## 2. Prerequisites for Local Development
(No changes from previous version of README)
- **Java JDK**: Version 17 or higher.
- **Apache Maven**: Version 3.6+ (for building the project).
- **Docker & Docker Compose**: For running HashiCorp Vault, PostgreSQL, Apache Kafka, and an S3-compatible service (e.g., LocalStack) locally.
- **HashiCorp Vault Instance**: (Details as before)
- **PostgreSQL Instance**: (Details as before)
- **Apache Kafka Instance**.
- **S3-Compatible Storage**: (Details as before, bucket name `mysillydreams-vendor-docs` or `test-bucket` for tests can be shared or specific per module via config).

## 3. Configuration

The service is configured via `src/main/resources/bootstrap.yml` and `src/main/resources/application.yml`.

### Key Environment Variables & Application Properties:
(Previous keys remain)
- `db.url`, `db.username`, `db.password`
- `KAFKA_BROKERS`
- `spring.cloud.vault.transit.default-key-name`
- `VENDOR_DOCS_S3_BUCKET`, `AWS_S3_REGION`, `AWS_S3_ENDPOINT_OVERRIDE`
- `KYC_TOPIC_START`, `KYC_TOPIC_DOCUMENT_UPLOADED`
- `INVENTORY_TOPIC_ITEM_CREATED`, `INVENTORY_TOPIC_STOCK_ADJUSTED`
- `APP_INTERNAL_API_SECRET_KEY_USER_SVC`
- **New for Delivery & Support:**
  - `DELIVERY_PHOTO_S3_BUCKET` (S3 bucket for delivery photos, can default to `VENDOR_DOCS_S3_BUCKET`).
  - `DELIVERY_TOPIC_ORDER_ASSIGNED` (default: `order.assigned.v1`)
  - `DELIVERY_TOPIC_STATUS_CHANGED` (default: `delivery.status.changed.v1`)
  - `SUPPORT_TOPIC_TICKET_CREATED` (default: `support.ticket.created.v1`)
  - `SUPPORT_TOPIC_TICKET_UPDATED` (default: `support.ticket.updated.v1`)

### Important Production Settings:
(All previous points remain valid)

## 4. Building and Running Locally
(No significant changes)

## 5. Running Tests
(Remains the same)

## 6. API Endpoints

Refer to the OpenAPI/Swagger documentation (`/swagger-ui.html`, `/v3/api-docs`).

### Core User Endpoints (`/users`): (No change)
### Vendor Onboarding Endpoints (`/vendor-onboarding`): (No change)
### Inventory Management Endpoints (`/inventory-onboarding`, `/inventory`): (No change)
### Admin Endpoints (`/admin`): (No change from previous version)
### Internal Admin Provisioning Endpoints (`/internal/users`): (No change)

### Delivery Operations Endpoints (`/delivery` - Require ROLE_DELIVERY_USER or ROLE_ADMIN):
- **`GET /assignments`**: List active assignments for the delivery user.
- **`POST /assignments/{assignmentId}/arrive`**: Mark arrival, record GPS.
- **`POST /assignments/{assignmentId}/call`**: Record a call event.
- **`POST /assignments/{assignmentId}/upload-photo`**: Upload delivery photo (multipart).
- **`POST /assignments/{assignmentId}/verify-otp`**: Verify delivery OTP.
- **`POST /assignments/{assignmentId}/complete`**: Complete an assignment.

### Support Ticket Endpoints (`/support`):
- **`POST /tickets`**: Create a new support ticket (customer action, `isAuthenticated()`).
- **`GET /tickets`**: List tickets (Support/Admin action: `ROLE_SUPPORT_USER` or `ROLE_ADMIN`).
- **`GET /tickets/{ticketId}`**: Get ticket details (Customer for own, Support/Admin for relevant/all).
- **`POST /tickets/{ticketId}/messages`**: Post a message to a ticket (Customer for own, Support/Admin for relevant/all).
- **`PUT /tickets/{ticketId}/status`**: Update ticket status (Support/Admin action).
- **`GET /tickets/customer/{customerId}`**: List tickets for a specific customer (Support/Admin action).

## 7. Kafka Events

### Events Produced by User Service:
(Previous events for Vendor and Inventory remain)
- **Topic**: `kyc.vendor.start.v1`
- **Topic**: `kyc.vendor.document.uploaded.v1`
- **Topic**: `inventory.item.created.v1`
- **Topic**: `inventory.stock.adjusted.v1`

- **New for Delivery:**
  - **Topic**: `order.assigned.v1` (configurable via `delivery.topic.orderAssigned`)
    - **Event Type**: `OrderAssigned`
    - **Payload**: Includes `assignmentId`, `orderId`, `deliveryProfileId`, `deliveryUserId`, `assignmentType`, `status`, `assignedAt`.
    - **Description**: Published when an order is assigned to a delivery user.
  - **Topic**: `delivery.status.changed.v1` (configurable via `delivery.topic.deliveryStatusChanged`)
    - **Event Type**: `DeliveryStatusChanged`
    - **Payload**: Includes `assignmentId`, `orderId`, `deliveryProfileId`, `newStatus`, `oldStatus`, `statusChangeTimestamp`.
    - **Description**: Published when a delivery assignment's status changes.

- **New for Support:**
  - **Topic**: `support.ticket.created.v1` (configurable via `support.topic.ticketCreated`)
    - **Event Type**: `SupportTicketCreated`
    - **Payload**: Includes `ticketId`, `customerId`, `subject`, `status`, `createdAt`, `assignedToSupportProfileId` (if any).
    - **Description**: Published when a new support ticket is created.
  - **Topic**: `support.ticket.updated.v1` (configurable via `support.topic.ticketUpdated`)
    - **Event Type**: `SupportTicketUpdated`
    - **Payload**: Includes `ticketId`, `customerId`, `newStatus`, `oldStatus` (if changed), `assignedToSupportProfileId`, `newMessageId` (if applicable), `updatedAt`.
    - **Description**: Published when a support ticket is updated (e.g., status change, new message, assignment).

*(TODO: Add `user.created`, `user.updated` events if/when implemented for core user changes).*

## 8. Security & Hardening Notes
(Previous points remain valid)
- **Authorization**: New roles `ROLE_DELIVERY_USER` and `ROLE_SUPPORT_USER` are introduced. Endpoints for Delivery and Support modules are protected using these roles (or `ROLE_ADMIN`). Fine-grained access control (e.g., delivery user accessing only their assignments, customer accessing only their tickets) is implemented or noted as TODOs in controllers/services.
- **PII in Delivery/Support**: Evaluate `DeliveryProfile.vehicleDetails`, `DeliveryEvent.payload`, `SupportTicket.subject/description`, `SupportMessage.message` for field-level encryption if they contain sensitive PII. TODOs are in place.
```
