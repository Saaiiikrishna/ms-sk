/**
 * Admin Management Service
 * Handles all admin-related API calls including the 3-step admin creation process
 */

import { 
  apiClient, 
  API_CONFIG, 
  ApiResponse,
  AdminCreationStep1Request,
  AdminCreationStep1Response,
  AdminCreationStep2Request,
  AdminCreationStep2Response,
  AdminCreationStep3Request,
  AdminCreationStep3Response
} from './api';

export interface AdminUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  department?: string;
  phoneNumber?: string;
  permissions: string[];
  mfaEnabled: boolean;
  createdAt: string;
  lastLogin?: string;
  status: 'active' | 'inactive' | 'pending';
}

export interface AdminListResponse {
  admins: AdminUser[];
  total: number;
  page: number;
  pageSize: number;
}

export class AdminService {
  /**
   * Step 1: Initialize admin creation with basic details and current admin MFA
   */
  async createAdminStep1(request: AdminCreationStep1Request): Promise<ApiResponse<AdminCreationStep1Response>> {
    return apiClient.post<AdminCreationStep1Response>(
      API_CONFIG.ENDPOINTS.ADMIN.CREATE_STEP1,
      request
    );
  }

  /**
   * Step 2: Generate QR code for new admin's MFA setup
   */
  async createAdminStep2(request: AdminCreationStep2Request): Promise<ApiResponse<AdminCreationStep2Response>> {
    return apiClient.post<AdminCreationStep2Response>(
      API_CONFIG.ENDPOINTS.ADMIN.CREATE_STEP2,
      request
    );
  }

  /**
   * Step 3: Verify new admin's MFA and finalize account creation
   */
  async createAdminStep3(request: AdminCreationStep3Request): Promise<ApiResponse<AdminCreationStep3Response>> {
    return apiClient.post<AdminCreationStep3Response>(
      API_CONFIG.ENDPOINTS.ADMIN.CREATE_STEP3,
      request
    );
  }

  /**
   * Get list of all admin users
   */
  async getAdmins(page: number = 1, pageSize: number = 10): Promise<ApiResponse<AdminListResponse>> {
    return apiClient.get<AdminListResponse>(
      `/api/admin/admins?page=${page}&pageSize=${pageSize}`
    );
  }

  /**
   * Get admin user by ID
   */
  async getAdminById(adminId: string): Promise<ApiResponse<AdminUser>> {
    return apiClient.get<AdminUser>(
      `/api/admin/admins/${adminId}`
    );
  }

  /**
   * Update admin user
   */
  async updateAdmin(adminId: string, updates: Partial<AdminUser>): Promise<ApiResponse<AdminUser>> {
    return apiClient.put<AdminUser>(
      `/api/admin/admins/${adminId}`,
      updates
    );
  }

  /**
   * Delete admin user
   */
  async deleteAdmin(adminId: string): Promise<ApiResponse<void>> {
    return apiClient.delete<void>(
      `/api/admin/admins/${adminId}`
    );
  }

  /**
   * Enable/Disable admin user
   */
  async toggleAdminStatus(adminId: string, status: 'active' | 'inactive'): Promise<ApiResponse<AdminUser>> {
    return apiClient.put<AdminUser>(
      `/api/admin/admins/${adminId}/status`,
      { status }
    );
  }

  /**
   * Reset admin MFA
   */
  async resetAdminMfa(adminId: string): Promise<ApiResponse<void>> {
    return apiClient.post<void>(
      `/api/admin/admins/${adminId}/reset-mfa`
    );
  }

  /**
   * Get admin permissions
   */
  async getAdminPermissions(adminId: string): Promise<ApiResponse<string[]>> {
    return apiClient.get<string[]>(
      `/api/admin/admins/${adminId}/permissions`
    );
  }

  /**
   * Update admin permissions
   */
  async updateAdminPermissions(adminId: string, permissions: string[]): Promise<ApiResponse<void>> {
    return apiClient.put<void>(
      `/api/admin/admins/${adminId}/permissions`,
      { permissions }
    );
  }

  /**
   * Validate admin creation session
   */
  async validateSession(sessionId: string): Promise<ApiResponse<{ valid: boolean; expiresAt: number }>> {
    return apiClient.get<{ valid: boolean; expiresAt: number }>(
      `/api/admin/sessions/${sessionId}/validate`
    );
  }

  /**
   * Cancel admin creation session
   */
  async cancelSession(sessionId: string): Promise<ApiResponse<void>> {
    return apiClient.delete<void>(
      `/api/admin/sessions/${sessionId}`
    );
  }
}

// Create singleton instance
export const adminService = new AdminService();
