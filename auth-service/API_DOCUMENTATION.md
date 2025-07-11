# Auth Service API Documentation

## Overview

The Auth Service provides authentication, authorization, and token management for the MySillyDreams Platform. It integrates with Keycloak for user management and provides service-specific JWT tokens for inter-service communication.

## Base URL

- **Development**: `http://localhost:8080`
- **Production**: `https://auth.mysillydreams.com`

## Authentication

Most endpoints require authentication via JWT Bearer tokens:

```
Authorization: Bearer <your-jwt-token>
```

## API Endpoints

### 1. Public Authentication Endpoints

#### POST /auth/login
Authenticates a user with username and password. For admin users with MFA enabled, an OTP is also required.

**Request Body:**
```json
{
  "username": "string",
  "password": "string",
  "otp": "string (optional, required for MFA-enabled admins)"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

**Error Responses:**
- `400 Bad Request`: Invalid request payload
- `401 Unauthorized`: Invalid credentials or MFA required
- `429 Too Many Requests`: Rate limit exceeded

#### POST /auth/refresh
Refreshes an existing JWT token.

**Request Body:**
```json
{
  "refreshToken": "your.current.jwt.token"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

#### GET /auth/validate
Validates a JWT token provided in the Authorization header.

**Headers:**
```
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "status": "valid",
  "user": "username",
  "authorities": ["ROLE_USER", "ROLE_ADMIN"]
}
```

**Response (401 Unauthorized):**
```json
{
  "status": "invalid"
}
```

#### POST /auth/password-rotate
Initiates password rotation for a user. Requires admin privileges.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Query Parameters:**
- `userId` (UUID): The ID of the user whose password should be rotated

**Response (200 OK):**
```json
{
  "message": "Password rotation initiated successfully"
}
```

### 2. Admin MFA Management Endpoints

These endpoints require `ROLE_ADMIN` and valid JWT authentication.

#### POST /auth/admin/mfa/setup
Generates a new TOTP secret and QR code for the authenticated admin user.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Response (200 OK):**
```json
{
  "rawSecret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
}
```

#### POST /auth/admin/mfa/verify
Verifies the provided OTP and enables MFA for the authenticated admin.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Request Body:**
```json
{
  "otp": "123456"
}
```

**Response (200 OK):**
```json
{
  "message": "MFA enabled successfully"
}
```

### 3. Internal Admin Provisioning Endpoints

These endpoints are for internal system use only and require the `X-Internal-API-Key` header.

#### POST /internal/auth/provision-mfa-setup
Provisions MFA setup for a specified admin user. Used by internal systems for automated admin onboarding.

**Headers:**
```
X-Internal-API-Key: <internal-api-secret>
```

**Request Body:**
```json
{
  "adminUserId": "550e8400-e29b-41d4-a716-446655440000",
  "adminUsername": "admin.user"
}
```

**Response (200 OK):**
```json
{
  "rawSecret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
}
```

### 4. Health and Monitoring Endpoints

#### GET /actuator/health
Returns the health status of the service.

**Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "kafka": {
      "status": "UP"
    }
  }
}
```

#### GET /actuator/info
Returns information about the service.

#### GET /actuator/prometheus
Returns Prometheus metrics (if enabled).

## Error Handling

All endpoints return standardized error responses:

```json
{
  "error": "Error description",
  "timestamp": 1640995200000,
  "status": 400
}
```

### Common Error Codes

- `400 Bad Request`: Invalid request format or missing required fields
- `401 Unauthorized`: Authentication required or invalid credentials
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Unexpected server error

## Rate Limiting

The login endpoint is rate-limited to prevent brute force attacks:
- **Limit**: 5 requests per 15-minute window per IP address
- **Response**: `429 Too Many Requests` when limit is exceeded

## Security Headers

All responses include security headers:
- `Strict-Transport-Security`: Enforces HTTPS
- `X-Content-Type-Options`: Prevents MIME sniffing
- `X-Frame-Options`: Prevents clickjacking
- `X-XSS-Protection`: Enables XSS protection
- `Content-Security-Policy`: Restricts resource loading
- `Referrer-Policy`: Controls referrer information

## Integration Examples

### Frontend Login Flow

```javascript
// 1. Login
const loginResponse = await fetch('/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'user@example.com',
    password: 'password123',
    otp: '123456' // Only for MFA-enabled admins
  })
});

const { accessToken } = await loginResponse.json();

// 2. Use token for authenticated requests
const apiResponse = await fetch('/api/protected-resource', {
  headers: { 'Authorization': `Bearer ${accessToken}` }
});
```

### Service-to-Service Token Validation

```javascript
// Validate token from another service
const validationResponse = await fetch('/auth/validate', {
  headers: { 'Authorization': `Bearer ${receivedToken}` }
});

const validation = await validationResponse.json();
if (validation.status === 'valid') {
  // Token is valid, proceed with request
  const userId = validation.user;
  const roles = validation.authorities;
}
```

## Kafka Events

The service publishes the following events:

### auth.events Topic

#### auth.user.password_rotated
Published when an admin initiates password rotation for a user.

**Event Payload:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "rotatedAt": "2023-12-01T10:15:30Z"
}
```

## Environment Configuration

### Required Environment Variables

- `DB_HOST`: Database host
- `DB_PORT`: Database port
- `DB_NAME`: Database name
- `DB_USER`: Database username
- `DB_PASS`: Database password
- `KEYCLOAK_URL`: Keycloak server URL
- `KEYCLOAK_SECRET`: Keycloak client secret
- `JWT_SECRET`: JWT signing secret (minimum 64 characters)
- `KAFKA_BROKER`: Kafka broker URL
- `APP_SIMPLE_ENCRYPTION_SECRET_KEY`: Encryption key for TOTP secrets
- `APP_INTERNAL_API_SECRET_KEY`: Secret key for internal API endpoints

### Optional Environment Variables

- `APP_CORS_ALLOWED_ORIGINS`: Comma-separated list of allowed CORS origins
- `APP_MFA_ISSUER_NAME`: MFA issuer name for TOTP apps
- `SPRING_PROFILES_ACTIVE`: Active Spring profiles

## OpenAPI/Swagger Documentation

Interactive API documentation is available at:
- **Swagger UI**: `/swagger-ui.html`
- **OpenAPI JSON**: `/v3/api-docs`

## Support and Contact

For API support or questions, please contact the development team or refer to the project documentation.
