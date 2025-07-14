/**
 * Authentication Service
 * Handles all authentication-related API calls
 */

import { 
  apiClient, 
  API_CONFIG, 
  ApiResponse, 
  LoginRequest, 
  LoginResponse 
} from './api';

export interface MfaSetupResponse {
  qrCodeDataUri: string;
  rawSecret: string;
  message: string;
}

export interface MfaVerificationRequest {
  otpCode: string;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}

export interface TokenValidationResponse {
  valid: boolean;
  user?: {
    id: string;
    username: string;
    email: string;
    roles: string[];
  };
  expiresAt?: number;
}

export class AuthService {
  /**
   * Login with email/password and optional MFA
   */
  async login(credentials: LoginRequest): Promise<ApiResponse<LoginResponse>> {
    const response = await apiClient.post<LoginResponse>(
      API_CONFIG.ENDPOINTS.AUTH.LOGIN,
      credentials
    );

    // If login successful, store the token
    if (response.success && response.data?.token) {
      apiClient.setToken(response.data.token);
    }

    return response;
  }

  /**
   * Refresh authentication token
   */
  async refreshToken(refreshToken: string): Promise<ApiResponse<LoginResponse>> {
    const response = await apiClient.post<LoginResponse>(
      API_CONFIG.ENDPOINTS.AUTH.REFRESH,
      { refreshToken }
    );

    // If refresh successful, update the stored token
    if (response.success && response.data?.token) {
      apiClient.setToken(response.data.token);
    }

    return response;
  }

  /**
   * Validate current token
   */
  async validateToken(): Promise<ApiResponse<TokenValidationResponse>> {
    return apiClient.get<TokenValidationResponse>(
      API_CONFIG.ENDPOINTS.AUTH.VALIDATE
    );
  }

  /**
   * Setup MFA for admin user
   */
  async setupMfa(): Promise<ApiResponse<MfaSetupResponse>> {
    return apiClient.post<MfaSetupResponse>(
      API_CONFIG.ENDPOINTS.ADMIN.MFA_SETUP
    );
  }

  /**
   * Verify MFA code
   */
  async verifyMfa(request: MfaVerificationRequest): Promise<ApiResponse<any>> {
    return apiClient.post(
      API_CONFIG.ENDPOINTS.ADMIN.MFA_VERIFY,
      request
    );
  }

  /**
   * Rotate user password (admin only)
   */
  async rotatePassword(userId: string): Promise<ApiResponse<any>> {
    return apiClient.post(
      `${API_CONFIG.ENDPOINTS.AUTH.PASSWORD_ROTATE}?userId=${userId}`
    );
  }

  /**
   * Logout user
   */
  logout(): void {
    apiClient.clearToken();
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return apiClient.isAuthenticated();
  }

  /**
   * Get current token
   */
  getToken(): string | null {
    return apiClient.getToken();
  }

  /**
   * Auto-refresh token if needed
   */
  async autoRefreshToken(): Promise<boolean> {
    const token = this.getToken();
    if (!token) return false;

    try {
      // Decode JWT to check expiration (basic implementation)
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Date.now() / 1000;
      
      // If token expires in less than 5 minutes, refresh it
      if (payload.exp - currentTime < 300) {
        const refreshToken = localStorage.getItem('refresh_token');
        if (refreshToken) {
          const response = await this.refreshToken(refreshToken);
          return response.success;
        }
      }
      
      return true;
    } catch (error) {
      console.error('Error checking token expiration:', error);
      return false;
    }
  }
}

// Create singleton instance
export const authService = new AuthService();
