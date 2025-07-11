/**
 * User Management Service
 * Handles all user-related API calls
 */

import { 
  apiClient, 
  API_CONFIG, 
  ApiResponse 
} from './api';

export interface User {
  id: string;
  referenceId: string;
  name: string;
  email: string;
  phone: string;
  role: 'customer' | 'vendor' | 'delivery' | 'support';
  status: 'active' | 'inactive' | 'pending' | 'suspended';
  mfaEnabled: boolean;
  lastLogin?: string;
  createdAt: string;
  updatedAt: string;
  profile?: {
    address?: string;
    city?: string;
    state?: string;
    zipCode?: string;
    country?: string;
    dateOfBirth?: string;
    profilePictureUrl?: string;
  };
}

export interface CreateUserRequest {
  name: string;
  email: string;
  phone: string;
  role: 'customer' | 'vendor' | 'delivery' | 'support';
  profile?: {
    address?: string;
    city?: string;
    state?: string;
    zipCode?: string;
    country?: string;
    dateOfBirth?: string;
  };
}

export interface UpdateUserRequest {
  name?: string;
  email?: string;
  phone?: string;
  role?: 'customer' | 'vendor' | 'delivery' | 'support';
  status?: 'active' | 'inactive' | 'pending' | 'suspended';
  profile?: {
    address?: string;
    city?: string;
    state?: string;
    zipCode?: string;
    country?: string;
    dateOfBirth?: string;
  };
}

export interface UserListResponse {
  users: User[];
  total: number;
  page: number;
  pageSize: number;
}

export interface UserSearchFilters {
  role?: string;
  status?: string;
  search?: string;
  page?: number;
  pageSize?: number;
}

export class UserService {
  /**
   * Create a new user
   */
  async createUser(userData: CreateUserRequest): Promise<ApiResponse<User>> {
    return apiClient.post<User>(
      API_CONFIG.ENDPOINTS.USERS.CREATE,
      userData
    );
  }

  /**
   * Get user by reference ID
   */
  async getUserByReferenceId(referenceId: string): Promise<ApiResponse<User>> {
    return apiClient.get<User>(
      API_CONFIG.ENDPOINTS.USERS.BY_ID(referenceId)
    );
  }

  /**
   * Update user by reference ID
   */
  async updateUser(referenceId: string, updates: UpdateUserRequest): Promise<ApiResponse<User>> {
    return apiClient.put<User>(
      API_CONFIG.ENDPOINTS.USERS.UPDATE(referenceId),
      updates
    );
  }

  /**
   * Delete user by reference ID
   */
  async deleteUser(referenceId: string): Promise<ApiResponse<void>> {
    return apiClient.delete<void>(
      API_CONFIG.ENDPOINTS.USERS.DELETE(referenceId)
    );
  }

  /**
   * Get list of users with filters
   */
  async getUsers(filters: UserSearchFilters = {}): Promise<ApiResponse<UserListResponse>> {
    const queryParams = new URLSearchParams();
    
    if (filters.role) queryParams.append('role', filters.role);
    if (filters.status) queryParams.append('status', filters.status);
    if (filters.search) queryParams.append('search', filters.search);
    if (filters.page) queryParams.append('page', filters.page.toString());
    if (filters.pageSize) queryParams.append('pageSize', filters.pageSize.toString());

    const queryString = queryParams.toString();
    const endpoint = queryString ? `${API_CONFIG.ENDPOINTS.USERS.BASE}?${queryString}` : API_CONFIG.ENDPOINTS.USERS.BASE;

    return apiClient.get<UserListResponse>(endpoint);
  }

  /**
   * Search users by name or email
   */
  async searchUsers(query: string, page: number = 1, pageSize: number = 10): Promise<ApiResponse<UserListResponse>> {
    return this.getUsers({
      search: query,
      page,
      pageSize
    });
  }

  /**
   * Get users by role
   */
  async getUsersByRole(role: string, page: number = 1, pageSize: number = 10): Promise<ApiResponse<UserListResponse>> {
    return this.getUsers({
      role,
      page,
      pageSize
    });
  }

  /**
   * Get users by status
   */
  async getUsersByStatus(status: string, page: number = 1, pageSize: number = 10): Promise<ApiResponse<UserListResponse>> {
    return this.getUsers({
      status,
      page,
      pageSize
    });
  }

  /**
   * Toggle user status (activate/deactivate)
   */
  async toggleUserStatus(referenceId: string, status: 'active' | 'inactive'): Promise<ApiResponse<User>> {
    return this.updateUser(referenceId, { status });
  }

  /**
   * Reset user password (admin only)
   */
  async resetUserPassword(referenceId: string): Promise<ApiResponse<{ temporaryPassword: string }>> {
    return apiClient.post<{ temporaryPassword: string }>(
      `/api/users/${referenceId}/reset-password`
    );
  }

  /**
   * Enable/Disable user MFA
   */
  async toggleUserMfa(referenceId: string, enabled: boolean): Promise<ApiResponse<User>> {
    return apiClient.put<User>(
      `/api/users/${referenceId}/mfa`,
      { enabled }
    );
  }

  /**
   * Get user activity log
   */
  async getUserActivity(referenceId: string, page: number = 1, pageSize: number = 20): Promise<ApiResponse<any[]>> {
    return apiClient.get<any[]>(
      `/api/users/${referenceId}/activity?page=${page}&pageSize=${pageSize}`
    );
  }

  /**
   * Export users data
   */
  async exportUsers(filters: UserSearchFilters = {}): Promise<ApiResponse<{ downloadUrl: string }>> {
    const queryParams = new URLSearchParams();
    
    if (filters.role) queryParams.append('role', filters.role);
    if (filters.status) queryParams.append('status', filters.status);
    if (filters.search) queryParams.append('search', filters.search);

    const queryString = queryParams.toString();
    const endpoint = queryString ? `/api/users/export?${queryString}` : '/api/users/export';

    return apiClient.get<{ downloadUrl: string }>(endpoint);
  }
}

// Create singleton instance
export const userService = new UserService();
