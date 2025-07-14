/**
 * API Configuration and Base Service
 * Handles all communication with the MySillyDreams backend through API Gateway
 */

// API Configuration
export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  ENDPOINTS: {
    // Authentication endpoints
    AUTH: {
      LOGIN: '/api/auth/login',
      REFRESH: '/api/auth/refresh',
      VALIDATE: '/api/auth/validate',
      PASSWORD_ROTATE: '/api/auth/password-rotate'
    },
    // Admin management endpoints
    ADMIN: {
      MFA_SETUP: '/api/auth/admin/mfa/setup',
      MFA_VERIFY: '/api/auth/admin/mfa/verify',
      CREATE_STEP1: '/api/auth/admin/admins/create/step1',
      CREATE_STEP2: '/api/auth/admin/admins/create/step2',
      CREATE_STEP3: '/api/auth/admin/admins/create/step3'
    },
    // User management endpoints
    USERS: {
      BASE: '/api/users',
      BY_ID: (id: string) => `/api/users/${id}`,
      CREATE: '/api/users',
      UPDATE: (id: string) => `/api/users/${id}`,
      DELETE: (id: string) => `/api/users/${id}`
    },
    // Configuration endpoints
    CONFIG: {
      BASE: '/api/config',
      BY_ENV_SERVICE: (env: string, service: string) => `/api/config/${env}/${service}`,
      BY_ENV_SERVICE_KEY: (env: string, service: string, key: string) => `/api/config/${env}/${service}/${key}`
    }
  }
};

// Request/Response Types
export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  error?: string;
  timestamp?: number;
}

export interface LoginRequest {
  username: string;
  password: string;
  otpCode?: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  expiresIn: number;
  user: {
    id: string;
    username: string;
    email: string;
    roles: string[];
    mfaEnabled: boolean;
  };
}

export interface AdminCreationStep1Request {
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  department?: string;
  phoneNumber?: string;
  permissions: string[];
  currentAdminMfaCode: string;
}

export interface AdminCreationStep1Response {
  sessionId: string;
  message: string;
  expiresAt: number;
}

export interface AdminCreationStep2Request {
  sessionId: string;
}

export interface AdminCreationStep2Response {
  qrCodeUrl: string;
  secretKey: string;
  sessionId: string;
}

export interface AdminCreationStep3Request {
  sessionId: string;
  newAdminMfaCode: string;
}

export interface AdminCreationStep3Response {
  success: boolean;
  adminId: string;
  message: string;
}

// HTTP Client Class
export class ApiClient {
  private baseURL: string;
  private token: string | null = null;

  constructor(baseURL: string = API_CONFIG.BASE_URL) {
    this.baseURL = baseURL;
    this.loadTokenFromStorage();
  }

  private loadTokenFromStorage(): void {
    this.token = localStorage.getItem('auth_token');
  }

  private saveTokenToStorage(token: string): void {
    this.token = token;
    localStorage.setItem('auth_token', token);
  }

  private removeTokenFromStorage(): void {
    this.token = null;
    localStorage.removeItem('auth_token');
  }

  private getHeaders(): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    return headers;
  }

  private async handleResponse<T>(response: Response): Promise<ApiResponse<T>> {
    const contentType = response.headers.get('content-type');
    
    if (contentType && contentType.includes('application/json')) {
      const data = await response.json();
      
      if (!response.ok) {
        return {
          success: false,
          error: data.message || data.error || 'An error occurred',
          timestamp: Date.now()
        };
      }
      
      return {
        success: true,
        data,
        timestamp: Date.now()
      };
    } else {
      if (!response.ok) {
        return {
          success: false,
          error: `HTTP ${response.status}: ${response.statusText}`,
          timestamp: Date.now()
        };
      }
      
      return {
        success: true,
        data: null as T,
        timestamp: Date.now()
      };
    }
  }

  async get<T>(endpoint: string): Promise<ApiResponse<T>> {
    try {
      const response = await fetch(`${this.baseURL}${endpoint}`, {
        method: 'GET',
        headers: this.getHeaders(),
      });

      return this.handleResponse<T>(response);
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error',
        timestamp: Date.now()
      };
    }
  }

  async post<T>(endpoint: string, data?: any): Promise<ApiResponse<T>> {
    try {
      const response = await fetch(`${this.baseURL}${endpoint}`, {
        method: 'POST',
        headers: this.getHeaders(),
        body: data ? JSON.stringify(data) : undefined,
      });

      return this.handleResponse<T>(response);
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error',
        timestamp: Date.now()
      };
    }
  }

  async put<T>(endpoint: string, data?: any): Promise<ApiResponse<T>> {
    try {
      const response = await fetch(`${this.baseURL}${endpoint}`, {
        method: 'PUT',
        headers: this.getHeaders(),
        body: data ? JSON.stringify(data) : undefined,
      });

      return this.handleResponse<T>(response);
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error',
        timestamp: Date.now()
      };
    }
  }

  async delete<T>(endpoint: string): Promise<ApiResponse<T>> {
    try {
      const response = await fetch(`${this.baseURL}${endpoint}`, {
        method: 'DELETE',
        headers: this.getHeaders(),
      });

      return this.handleResponse<T>(response);
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error',
        timestamp: Date.now()
      };
    }
  }

  setToken(token: string): void {
    this.saveTokenToStorage(token);
  }

  clearToken(): void {
    this.removeTokenFromStorage();
  }

  getToken(): string | null {
    return this.token;
  }

  isAuthenticated(): boolean {
    return !!this.token;
  }
}

// Create singleton instance
export const apiClient = new ApiClient();
