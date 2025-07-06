# Service Endpoints

Below is a list of REST endpoints implemented in this repository grouped by microservice.

## auth-service
- `POST /auth/login` – Authenticate a user using `LoginRequest` body. Returns a `JwtResponse` with token and expiry.
- `POST /auth/refresh` – Refresh a JWT using `TokenRefreshRequest`. Returns new `JwtResponse`.
- `GET /auth/validate` – Validate a JWT provided in `Authorization` header. Returns token status and user info.
- `POST /auth/password-rotate` – Rotate a user's password. Admin only. Parameter `userId`.
- `POST /auth/admin/mfa/setup` – Admin sets up MFA. Returns secret/QR code.
- `POST /auth/admin/mfa/verify` – Admin verifies OTP to enable MFA.
- `POST /internal/auth/provision-mfa-setup` – Internal endpoint protected via API key for provisioning admin MFA.

## user-service
### Public User APIs
- `POST /users` – Create new user profile. Body `UserDto`.
- `GET /users/{referenceId}` – Get user by reference ID.
- `PUT /users/{referenceId}` – Update user profile by reference ID.

### Admin APIs (`/admin`)
- `GET /admin/users/all-including-archived` – List all users including archived ones.
- `GET /admin/users` – List active users only.
- `GET /admin/users/archived` – List archived users.
- `GET /admin/users/{userId}` – Retrieve any user by UUID.
- `DELETE /admin/users/{userId}/soft` – Soft delete a user by UUID.
- `DELETE /admin/users/{userId}/force` – Placeholder for hard delete.
- `GET /admin/vendor-profiles` – List vendor profiles (paginated).
- `GET /admin/inventory-profiles` – List inventory profiles (paginated).

### Delivery APIs (`/delivery`)
- `GET /delivery/assignments` – List active assignments for authenticated delivery user.
- `POST /delivery/assignments/{assignmentId}/arrive` – Mark arrival at destination with GPS data.
- `POST /delivery/assignments/{assignmentId}/call` – Record call event.
- `POST /delivery/assignments/{assignmentId}/upload-photo` – Upload delivery photo file.
- `POST /delivery/assignments/{assignmentId}/verify-otp` – Verify customer OTP.
- `POST /delivery/assignments/{assignmentId}/complete` – Mark assignment completed.

### Inventory APIs (`/inventory`)
- `POST /inventory/items` – Add inventory item for a profile.
- `GET /inventory/items` – List items for a profile.
- `POST /inventory/items/{itemId}/adjust` – Adjust stock of an item.

### Inventory Onboarding (`/inventory-onboarding`)
- `POST /inventory-onboarding/register` – Register an existing user as inventory user.
- `GET /inventory-onboarding/profile` – Get inventory profile for user.

### Vendor Onboarding (`/vendor-onboarding`)
- `POST /vendor-onboarding/register` – Register new vendor profile.
- `GET /vendor-onboarding/profile` – Get vendor profile for authenticated user.
- `POST /vendor-onboarding/documents/upload-url` – Generate pre-signed URL for document upload.

### Support (`/support`)
- `POST /support/tickets` – Create support ticket.
- `GET /support/tickets` – List tickets (for support/admin).
- `GET /support/tickets/{ticketId}` – Get ticket details.
- `POST /support/tickets/{ticketId}/messages` – Post message to ticket.
- `PUT /support/tickets/{ticketId}/status` – Update ticket status.
- `GET /support/tickets/customer/{customerId}` – List tickets by customer ID.

### Internal Users (`/internal/users`)
- `POST /internal/users/provision-admin` – Provision an admin user via API key.

## order-api
- `POST /orders` – Create a new order with `CreateOrderRequest`. Requires `Idempotency-Key` header and auth token.
- `PUT /orders/{id}/cancel` – Cancel an order with reason query parameter.

## order-core
- `GET /internal/orders/{id}` – Internal endpoint to fetch order details.
- `POST /internal/orders/{id}/cancel` – Internal cancel order with body containing reason.

## inventory-api
- `GET /inventory/{sku}` – Get stock level for SKU.
- `POST /inventory/adjust` – Adjust stock based on `AdjustStockRequest`.
- `POST /inventory/reserve` – Reserve inventory using `ReservationRequestDto`.

## inventory-core
- `GET /internal/inventory/{sku}` – Retrieve raw stock level record.

## delivery-service
- `POST /delivery/assignments/{assignmentId}/arrive-pickup` – Mark courier arrival at pickup.
- `POST /delivery/assignments/{assignmentId}/pickup-photo` – Record pickup photo and OTP.
- `POST /delivery/assignments/{assignmentId}/gps` – Submit GPS update.
- `POST /delivery/assignments/{assignmentId}/arrive-dropoff` – Mark arrival at dropoff.
- `POST /delivery/assignments/{assignmentId}/deliver` – Record delivery details.

## payment-service
- `POST /webhook/razorpay` – Single endpoint to handle Razorpay webhook events.

## catalog-service
- `POST /api/v1/items` – Create catalog item.
- `GET /api/v1/items/{id}` – Get item by ID.
- `GET /api/v1/items/sku/{sku}` – Get item by SKU.
- `GET /api/v1/items` – List items (paged).
- `GET /api/v1/items/category/{categoryId}` – List items by category.
- `PUT /api/v1/items/{id}` – Update item.
- `PUT /api/v1/items/{id}/price` – Update base price.
- `DELETE /api/v1/items/{id}` – Delete item.
- `GET /api/v1/items/search` – Search items with filters.

- `POST /api/v1/cart` – Get or create cart for user.
- `GET /api/v1/cart` – Get active cart.
- `POST /api/v1/cart/items` – Add item to cart.
- `PUT /api/v1/cart/items/{catalogItemId}` – Update quantity of item in cart.
- `DELETE /api/v1/cart/items/{catalogItemId}` – Remove item from cart.
- `GET /api/v1/cart/total` – Get cart totals.
- `POST /api/v1/cart/checkout` – Checkout cart.

- `POST /api/v1/categories` – Create category.
- `GET /api/v1/categories` – List top level categories.
- `GET /api/v1/categories/{id}` – Get category by ID.
- `GET /api/v1/categories/{id}/subcategories` – List subcategories.
- `PUT /api/v1/categories/{id}` – Update category.
- `DELETE /api/v1/categories/{id}` – Delete category.

- `POST /api/v1/stock/adjust` – Adjust stock for item.
- `GET /api/v1/stock/{itemId}` – Get stock level for item.
- `GET /api/v1/stock/levels` – List all stock levels (paged).
- `GET /api/v1/stock/below-reorder` – List items below reorder level.

- `POST /api/v1/pricing/items/{itemId}/bulk-rules` – Create bulk pricing rule.
- `GET /api/v1/pricing/items/{itemId}/bulk-rules` – List bulk rules for item.
- `GET /api/v1/pricing/bulk-rules/{ruleId}` – Get bulk rule by ID.
- `PUT /api/v1/pricing/bulk-rules/{ruleId}` – Update bulk rule.
- `DELETE /api/v1/pricing/bulk-rules/{ruleId}` – Delete bulk rule.
- `GET /api/v1/pricing/items/{itemId}/price-detail` – Get calculated price detail for quantity.
- `GET /api/v1/pricing/items/{itemId}/price-history` – Get price history for item.

- `POST /api/v1/pricing/overrides` – Create price override.
- `GET /api/v1/pricing/overrides/{overrideId}` – Get price override by ID.
- `GET /api/v1/pricing/overrides/item/{itemId}` – List overrides for item.
- `GET /api/v1/pricing/overrides/item/{itemId}/active` – List active overrides for item.
- `PUT /api/v1/pricing/overrides/{overrideId}` – Update price override.
- `DELETE /api/v1/pricing/overrides/{overrideId}` – Delete price override.

- `POST /api/v1/pricing/dynamic-rules` – Create dynamic pricing rule.
- `GET /api/v1/pricing/dynamic-rules/{ruleId}` – Get dynamic pricing rule by ID.
- `GET /api/v1/pricing/dynamic-rules` – List dynamic pricing rules.
- `GET /api/v1/pricing/dynamic-rules/item/{itemId}` – List dynamic rules for item.
- `PUT /api/v1/pricing/dynamic-rules/{ruleId}` – Update dynamic rule.
- `DELETE /api/v1/pricing/dynamic-rules/{ruleId}` – Delete dynamic rule.

## pricing-engine
(No REST controllers present in code.)

