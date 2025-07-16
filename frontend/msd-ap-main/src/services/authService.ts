/**
 * Authentication Service
 * Handles all authentication-related API calls
 */

import {
  apiClient,
  API_CONFIG,
  ApiResponse,
  LoginRequest,
  LoginResponse,
  JwtResponse
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
    // Call backend which returns JwtResponse
    const backendResponse = await apiClient.post<JwtResponse>(
      API_CONFIG.ENDPOINTS.AUTH.LOGIN,
      credentials
    );

    if (!backendResponse.success || !backendResponse.data) {
      return {
        success: false,
        error: backendResponse.error || 'Login failed',
        timestamp: backendResponse.timestamp
      };
    }

    // Convert JwtResponse to LoginResponse format
    const jwtData = backendResponse.data;

    // Store the token
    apiClient.setToken(jwtData.accessToken);

    // Get user info from token validation
    const userInfo = await this.validateToken();

    const loginResponse: LoginResponse = {
      token: jwtData.accessToken,
      refreshToken: jwtData.refreshToken,
      expiresIn: jwtData.expiresIn,
      user: userInfo.success && userInfo.data ? userInfo.data.user! : {
        id: '',
        username: credentials.username,
        email: '',
        roles: [],
        mfaEnabled: false
      }
    };

    return {
      success: true,
      data: loginResponse,
      timestamp: backendResponse.timestamp
    };
  }

  /**
   * Refresh authentication token
   */
  async refreshToken(refreshToken: string): Promise<ApiResponse<LoginResponse>> {
    // Call backend which returns JwtResponse
    const backendResponse = await apiClient.post<JwtResponse>(
      API_CONFIG.ENDPOINTS.AUTH.REFRESH,
      { refreshToken }
    );

    if (!backendResponse.success || !backendResponse.data) {
      return {
        success: false,
        error: backendResponse.error || 'Token refresh failed',
        timestamp: backendResponse.timestamp
      };
    }

    // Convert JwtResponse to LoginResponse format
    const jwtData = backendResponse.data;

    // Store the new token
    apiClient.setToken(jwtData.accessToken);

    // Get user info from token validation
    const userInfo = await this.validateToken();

    const loginResponse: LoginResponse = {
      token: jwtData.accessToken,
      refreshToken: jwtData.refreshToken,
      expiresIn: jwtData.expiresIn,
      user: userInfo.success && userInfo.data ? userInfo.data.user! : {
        id: '',
        username: '',
        email: '',
        roles: [],
        mfaEnabled: false
      }
    };

    return {
      success: true,
      data: loginResponse,
      timestamp: backendResponse.timestamp
    };
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
      // Safely decode JWT to check expiration
      const parts = token.split('.');
      if (parts.length !== 3) {
        console.error('Invalid JWT token format');
        return false;
      }

      // Add padding if needed for base64 decoding
      let payload = parts[1];
      while (payload.length % 4) {
        payload += '=';
      }

      const decodedPayload = JSON.parse(atob(payload));
      const currentTime = Date.now() / 1000;

      // Check if token is expired or expires in less than 5 minutes
      if (decodedPayload.exp && decodedPayload.exp - currentTime < 300) {
        const refreshToken = localStorage.getItem('refresh_token');
        if (refreshToken) {
          const response = await this.refreshToken(refreshToken);
          return response.success;
        }
        return false;
      }

      return true;
    } catch (error) {
      console.error('Error checking token expiration:', error);
      // Clear potentially corrupted token
      this.logout();
      return false;
    }
  }
}

// Create singleton instance
export const authService = new AuthService();
