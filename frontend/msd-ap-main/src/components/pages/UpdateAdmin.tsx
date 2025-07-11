import React, { useState, useEffect } from 'react';
import { Shield, ArrowLeft, Save, User, Mail, Phone, Building } from 'lucide-react';
import { PageType } from '../../App';

interface UpdateAdminProps {
  adminId: string;
  onPageChange: (page: PageType) => void;
}

interface AdminData {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  department: string;
  phoneNumber: string;
  status: 'Active' | 'Inactive';
  permissions: string[];
}

export const UpdateAdmin: React.FC<UpdateAdminProps> = ({ adminId, onPageChange }) => {
  const [adminData, setAdminData] = useState<AdminData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [showMfaModal, setShowMfaModal] = useState(false);
  const [mfaCode, setMfaCode] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);

  const availablePermissions = [
    { id: 'user_management', label: 'User Management', description: 'Create, update, and delete user accounts' },
    { id: 'vendor_management', label: 'Vendor Management', description: 'Manage vendor accounts and KYC' },
    { id: 'inventory_management', label: 'Inventory Management', description: 'Manage product inventory and stock' },
    { id: 'delivery_management', label: 'Delivery Management', description: 'Manage delivery personnel and assignments' },
    { id: 'support_management', label: 'Support Management', description: 'Handle customer support tickets' },
    { id: 'catalog_management', label: 'Catalog Management', description: 'Manage products and categories' },
    { id: 'system_settings', label: 'System Settings', description: 'Configure platform settings' },
    { id: 'admin_management', label: 'Admin Management', description: 'Create and manage administrator accounts' }
  ];

  useEffect(() => {
    // Simulate loading admin data
    const loadAdminData = async () => {
      setIsLoading(true);
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      // Mock data - in real app, fetch from API
      const mockAdmin: AdminData = {
        id: parseInt(adminId),
        firstName: 'Sarah',
        lastName: 'Manager',
        email: 'sarah.manager@mysillydeams.com',
        role: 'Administrator',
        department: 'Support',
        phoneNumber: '+1 (555) 987-6543',
        status: 'Active',
        permissions: ['user_management', 'support_management', 'catalog_management']
      };
      
      setAdminData(mockAdmin);
      setIsLoading(false);
    };

    loadAdminData();
  }, [adminId]);

  const handleInputChange = (field: keyof AdminData, value: string | string[]) => {
    if (adminData) {
      setAdminData(prev => ({
        ...prev!,
        [field]: value
      }));
    }
  };

  const handlePermissionToggle = (permissionId: string) => {
    if (adminData) {
      setAdminData(prev => ({
        ...prev!,
        permissions: prev!.permissions.includes(permissionId)
          ? prev!.permissions.filter(p => p !== permissionId)
          : [...prev!.permissions, permissionId]
      }));
    }
  };

  const handleSave = () => {
    setShowMfaModal(true);
  };

  const handleMfaVerification = async () => {
    setIsVerifying(true);
    
    // Simulate API call for MFA verification
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    if (mfaCode === '123456') { // Mock verification
      setIsSaving(true);
      
      // Simulate admin update
      await new Promise(resolve => setTimeout(resolve, 1500));
      
      alert('Admin updated successfully!');
      setShowMfaModal(false);
      setMfaCode('');
      setIsSaving(false);
      onPageChange('admins');
    } else {
      alert('Invalid MFA code. Please try again.');
      setIsVerifying(false);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading admin data...</p>
        </div>
      </div>
    );
  }

  if (!adminData) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600">Admin not found</p>
          <button
            onClick={() => onPageChange('admins')}
            className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            Back to Admin Management
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-4xl mx-auto px-4">
        <div className="mb-8">
          <button
            onClick={() => onPageChange('admins')}
            className="flex items-center space-x-2 text-gray-600 hover:text-gray-900 transition-colors"
          >
            <ArrowLeft size={20} />
            <span>Back to Admin Management</span>
          </button>
        </div>

        <div className="bg-white rounded-xl shadow-lg p-8">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Update Administrator</h1>
            <p className="text-gray-600">Modify administrator details and permissions</p>
          </div>

          <div className="space-y-6">
            {/* Basic Information */}
            <div>
              <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center">
                <User className="w-5 h-5 mr-2" />
                Basic Information
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    First Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={adminData.firstName}
                    onChange={(e) => handleInputChange('firstName', e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Last Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={adminData.lastName}
                    onChange={(e) => handleInputChange('lastName', e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    required
                  />
                </div>
              </div>
            </div>

            {/* Contact Information */}
            <div>
              <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center">
                <Mail className="w-5 h-5 mr-2" />
                Contact Information
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Email Address <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="email"
                    value={adminData.email}
                    onChange={(e) => handleInputChange('email', e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Phone Number</label>
                  <input
                    type="tel"
                    value={adminData.phoneNumber}
                    onChange={(e) => handleInputChange('phoneNumber', e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>
              </div>
            </div>

            {/* Role and Department */}
            <div>
              <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center">
                <Building className="w-5 h-5 mr-2" />
                Role & Department
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Role</label>
                  <select
                    value={adminData.role}
                    onChange={(e) => handleInputChange('role', e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="Administrator">Administrator</option>
                    <option value="Super Administrator">Super Administrator</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Department</label>
                  <input
                    type="text"
                    value={adminData.department}
                    onChange={(e) => handleInputChange('department', e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="e.g., Operations, Support"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Status</label>
                  <select
                    value={adminData.status}
                    onChange={(e) => handleInputChange('status', e.target.value as 'Active' | 'Inactive')}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="Active">Active</option>
                    <option value="Inactive">Inactive</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Permissions */}
            <div>
              <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center">
                <Shield className="w-5 h-5 mr-2" />
                Permissions
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {availablePermissions.map((permission) => (
                  <div key={permission.id} className="border border-gray-200 rounded-lg p-4">
                    <label className="flex items-start space-x-3 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={adminData.permissions.includes(permission.id)}
                        onChange={() => handlePermissionToggle(permission.id)}
                        className="mt-1 w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                      />
                      <div>
                        <div className="font-medium text-gray-900">{permission.label}</div>
                        <div className="text-sm text-gray-500">{permission.description}</div>
                      </div>
                    </label>
                  </div>
                ))}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center justify-end space-x-4 pt-6 border-t">
              <button
                onClick={() => onPageChange('admins')}
                className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2"
              >
                <Save size={16} />
                <span>Save Changes</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* MFA Verification Modal */}
      {showMfaModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md">
            <div className="flex items-center justify-center mb-4">
              <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                <Shield className="w-6 h-6 text-blue-600" />
              </div>
            </div>
            <h3 className="text-lg font-semibold text-gray-900 text-center mb-2">
              MFA Verification Required
            </h3>
            <p className="text-gray-600 text-center mb-6">
              Please enter your 6-digit MFA code to update the administrator account.
            </p>
            <div className="mb-6">
              <input
                type="text"
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value)}
                placeholder="000000"
                maxLength={6}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent text-center tracking-widest text-lg"
              />
            </div>
            <div className="flex items-center space-x-4">
              <button
                onClick={() => {
                  setShowMfaModal(false);
                  setMfaCode('');
                }}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                disabled={isVerifying || isSaving}
              >
                Cancel
              </button>
              <button
                onClick={handleMfaVerification}
                disabled={mfaCode.length !== 6 || isVerifying || isSaving}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isVerifying ? 'Verifying...' : isSaving ? 'Saving...' : 'Verify & Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};