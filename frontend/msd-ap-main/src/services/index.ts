/**
 * Services Index
 * Exports all API services for easy importing
 */

// API Configuration and Base Client
export { 
  apiClient, 
  API_CONFIG, 
  ApiClient,
  type ApiResponse,
  type LoginRequest,
  type LoginResponse,
  type AdminCreationStep1Request,
  type AdminCreationStep1Response,
  type AdminCreationStep2Request,
  type AdminCreationStep2Response,
  type AdminCreationStep3Request,
  type AdminCreationStep3Response
} from './api';

// Authentication Service
export { 
  authService, 
  AuthService,
  type MfaSetupResponse,
  type MfaVerificationRequest,
  type TokenRefreshRequest,
  type TokenValidationResponse
} from './authService';

// Admin Management Service
export { 
  adminService, 
  AdminService,
  type AdminUser,
  type AdminListResponse
} from './adminService';

// User Management Service
export { 
  userService, 
  UserService,
  type User,
  type CreateUserRequest,
  type UpdateUserRequest,
  type UserListResponse,
  type UserSearchFilters
} from './userService';
