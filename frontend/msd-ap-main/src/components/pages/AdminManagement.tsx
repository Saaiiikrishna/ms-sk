import React, { useState } from 'react';
import { Shield, Plus, Search, Filter, Edit, Trash2, CheckCircle, XCircle, Eye, EyeOff } from 'lucide-react';
import { PageType } from '../../App';

interface Admin {
  id: number;
  name: string;
  email: string;
  role: string;
  status: 'Active' | 'Inactive';
  mfaEnabled: boolean;
  lastLogin: string;
  createdAt: string;
  createdBy: string;
}

interface AdminManagementProps {
  onPageChange: (page: PageType) => void;
  onSelectAdmin: (adminId: string) => void;
}

export const AdminManagement: React.FC<AdminManagementProps> = ({ onPageChange, onSelectAdmin }) => {
  const [selectedAdmin, setSelectedAdmin] = useState<Admin | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showMfaModal, setShowMfaModal] = useState(false);
  const [mfaAction, setMfaAction] = useState<'update' | 'delete' | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);

  const [admins] = useState<Admin[]>([
    {
      id: 1,
      name: 'John Admin',
      email: 'john.admin@mysillydeams.com',
      role: 'Super Administrator',
      status: 'Active',
      mfaEnabled: true,
      lastLogin: '2024-01-15 14:30',
      createdAt: '2024-01-01 10:00',
      createdBy: 'System'
    },
    {
      id: 2,
      name: 'Sarah Manager',
      email: 'sarah.manager@mysillydeams.com',
      role: 'Administrator',
      status: 'Active',
      mfaEnabled: true,
      lastLogin: '2024-01-15 12:15',
      createdAt: '2024-01-05 09:30',
      createdBy: 'John Admin'
    },
    {
      id: 3,
      name: 'Mike Supervisor',
      email: 'mike.supervisor@mysillydeams.com',
      role: 'Administrator',
      status: 'Inactive',
      mfaEnabled: false,
      lastLogin: '2024-01-10 16:45',
      createdAt: '2024-01-08 11:20',
      createdBy: 'John Admin'
    }
  ]);

  const handleMfaVerification = async () => {
    setIsVerifying(true);
    
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 1500));
    
    if (mfaCode === '123456') { // Mock verification
      if (mfaAction === 'update') {
        // Handle update action
        console.log('Admin updated successfully');
      } else if (mfaAction === 'delete') {
        // Handle delete action
        console.log('Admin deleted successfully');
        setShowDeleteModal(false);
      }
      setShowMfaModal(false);
      setMfaCode('');
      setMfaAction(null);
    } else {
      alert('Invalid MFA code. Please try again.');
    }
    
    setIsVerifying(false);
  };

  const handleUpdateAdmin = (admin: Admin) => {
    onSelectAdmin(admin.id.toString());
    onPageChange('update-admin');
  };

  const handleDeleteAdmin = (admin: Admin) => {
    setSelectedAdmin(admin);
    setMfaAction('delete');
    setShowDeleteModal(true);
  };

  const confirmDelete = () => {
    setShowDeleteModal(false);
    setShowMfaModal(true);
  };

  const getStatusColor = (status: string) => {
    return status === 'Active' ? 'text-green-600' : 'text-red-600';
  };

  const getRoleColor = (role: string) => {
    switch (role) {
      case 'Super Administrator': return 'bg-red-100 text-red-800';
      case 'Administrator': return 'bg-blue-100 text-blue-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Admin Management</h1>
          <p className="text-gray-600">Manage administrator accounts and permissions</p>
        </div>
        <button
          onClick={() => onPageChange('create-admin')}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2"
        >
          <Plus size={20} />
          <span>Create Admin</span>
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">Total Admins</p>
              <p className="text-2xl font-bold text-gray-900">{admins.length}</p>
            </div>
            <Shield className="w-8 h-8 text-blue-600" />
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">Active Admins</p>
              <p className="text-2xl font-bold text-green-600">
                {admins.filter(admin => admin.status === 'Active').length}
              </p>
            </div>
            <CheckCircle className="w-8 h-8 text-green-600" />
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">MFA Enabled</p>
              <p className="text-2xl font-bold text-blue-600">
                {admins.filter(admin => admin.mfaEnabled).length}
              </p>
            </div>
            <Shield className="w-8 h-8 text-blue-600" />
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1 min-w-64">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
              <input
                type="text"
                placeholder="Search admins..."
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>
          <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
            <option>All Roles</option>
            <option>Super Administrator</option>
            <option>Administrator</option>
          </select>
          <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
            <option>All Status</option>
            <option>Active</option>
            <option>Inactive</option>
          </select>
          <button className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors flex items-center space-x-2">
            <Filter size={16} />
            <span>More Filters</span>
          </button>
        </div>
      </div>

      {/* Admins Table */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Admin
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Role
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  MFA
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Last Login
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Created By
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {admins.map((admin) => (
                <tr key={admin.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center">
                        <Shield size={16} className="text-blue-600" />
                      </div>
                      <div className="ml-4">
                        <div className="text-sm font-medium text-gray-900">{admin.name}</div>
                        <div className="text-sm text-gray-500">{admin.email}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getRoleColor(admin.role)}`}>
                      {admin.role}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      {admin.status === 'Active' ? (
                        <CheckCircle size={16} className="text-green-500 mr-2" />
                      ) : (
                        <XCircle size={16} className="text-red-500 mr-2" />
                      )}
                      <span className={`text-sm font-medium ${getStatusColor(admin.status)}`}>
                        {admin.status}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      <Shield size={16} className={admin.mfaEnabled ? 'text-green-500' : 'text-gray-400'} />
                      <span className={`ml-2 text-sm ${admin.mfaEnabled ? 'text-green-600' : 'text-gray-500'}`}>
                        {admin.mfaEnabled ? 'Enabled' : 'Disabled'}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {admin.lastLogin}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {admin.createdBy}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={() => handleUpdateAdmin(admin)}
                        className="text-blue-600 hover:text-blue-900 p-1 rounded"
                        title="Edit Admin"
                      >
                        <Edit size={16} />
                      </button>
                      <button
                        onClick={() => handleDeleteAdmin(admin)}
                        className="text-red-600 hover:text-red-900 p-1 rounded"
                        title="Delete Admin"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteModal && selectedAdmin && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md">
            <div className="flex items-center justify-center mb-4">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <Trash2 className="w-6 h-6 text-red-600" />
              </div>
            </div>
            <h3 className="text-lg font-semibold text-gray-900 text-center mb-2">
              Delete Administrator
            </h3>
            <p className="text-gray-600 text-center mb-6">
              Are you sure you want to delete <strong>{selectedAdmin.name}</strong>? 
              This action cannot be undone and will require MFA verification.
            </p>
            <div className="flex items-center space-x-4">
              <button
                onClick={() => {
                  setShowDeleteModal(false);
                  setSelectedAdmin(null);
                }}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={confirmDelete}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
              >
                Continue
              </button>
            </div>
          </div>
        </div>
      )}

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
              Please enter your 6-digit MFA code to {mfaAction === 'delete' ? 'delete' : 'update'} the administrator account.
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
                  setMfaAction(null);
                  setSelectedAdmin(null);
                }}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                disabled={isVerifying}
              >
                Cancel
              </button>
              <button
                onClick={handleMfaVerification}
                disabled={mfaCode.length !== 6 || isVerifying}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isVerifying ? 'Verifying...' : 'Verify'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};