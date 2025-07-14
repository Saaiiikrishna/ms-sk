import React, { useState } from 'react';
import { Truck, Search, Filter, MapPin, Clock, CheckCircle, XCircle, User } from 'lucide-react';

export const DeliveryManagement: React.FC = () => {
  const [selectedAssignment, setSelectedAssignment] = useState<any>(null);

  const deliveryPersonnel = [
    {
      id: 1,
      name: 'Mike Johnson',
      email: 'mike.johnson@delivery.com',
      phone: '+1 (555) 123-4567',
      vehicleType: 'Motorcycle',
      licenseNumber: 'DL123456',
      status: 'Active',
      totalDeliveries: 1247,
      rating: 4.9,
      currentAssignments: 3
    },
    {
      id: 2,
      name: 'Sarah Wilson',
      email: 'sarah.wilson@delivery.com',
      phone: '+1 (555) 987-6543',
      vehicleType: 'Car',
      licenseNumber: 'DL789012',
      status: 'Active',
      totalDeliveries: 892,
      rating: 4.7,
      currentAssignments: 2
    },
    {
      id: 3,
      name: 'David Brown',
      email: 'david.brown@delivery.com',
      phone: '+1 (555) 456-7890',
      vehicleType: 'Bike',
      licenseNumber: 'DL345678',
      status: 'Inactive',
      totalDeliveries: 567,
      rating: 4.5,
      currentAssignments: 0
    }
  ];

  const deliveryAssignments = [
    {
      id: 1,
      orderId: 'ORD-2024-001',
      deliveryPersonnel: 'Mike Johnson',
      customer: 'John Doe',
      address: '123 Main St, New York, NY 10001',
      status: 'In Transit',
      estimatedDelivery: '2024-01-15 14:30',
      actualDelivery: null,
      distance: '5.2 km',
      assignedAt: '2024-01-15 12:00'
    },
    {
      id: 2,
      orderId: 'ORD-2024-002',
      deliveryPersonnel: 'Sarah Wilson',
      customer: 'Jane Smith',
      address: '456 Oak Ave, Brooklyn, NY 11201',
      status: 'Delivered',
      estimatedDelivery: '2024-01-15 13:00',
      actualDelivery: '2024-01-15 12:45',
      distance: '3.8 km',
      assignedAt: '2024-01-15 11:30'
    },
    {
      id: 3,
      orderId: 'ORD-2024-003',
      deliveryPersonnel: 'Mike Johnson',
      customer: 'Bob Johnson',
      address: '789 Pine St, Queens, NY 11375',
      status: 'Assigned',
      estimatedDelivery: '2024-01-15 16:00',
      actualDelivery: null,
      distance: '7.1 km',
      assignedAt: '2024-01-15 13:15'
    }
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Active': return 'text-green-600 bg-green-100';
      case 'Inactive': return 'text-gray-600 bg-gray-100';
      case 'Assigned': return 'text-blue-600 bg-blue-100';
      case 'In Transit': return 'text-yellow-600 bg-yellow-100';
      case 'Delivered': return 'text-green-600 bg-green-100';
      case 'Failed': return 'text-red-600 bg-red-100';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'Active': return <CheckCircle size={16} className="text-green-600" />;
      case 'Inactive': return <XCircle size={16} className="text-gray-600" />;
      case 'Assigned': return <Clock size={16} className="text-blue-600" />;
      case 'In Transit': return <Truck size={16} className="text-yellow-600" />;
      case 'Delivered': return <CheckCircle size={16} className="text-green-600" />;
      case 'Failed': return <XCircle size={16} className="text-red-600" />;
      default: return <Clock size={16} className="text-gray-600" />;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Delivery Management</h1>
          <p className="text-gray-600">Manage delivery personnel and assignments</p>
        </div>
        <div className="flex items-center space-x-4">
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <div className="flex items-center space-x-6">
              <div className="text-center">
                <div className="text-2xl font-bold text-green-600">2</div>
                <div className="text-sm text-gray-500">Active</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold text-yellow-600">1</div>
                <div className="text-sm text-gray-500">In Transit</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold text-blue-600">1</div>
                <div className="text-sm text-gray-500">Assigned</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Delivery Personnel */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Delivery Personnel</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {deliveryPersonnel.map((person) => (
            <div key={person.id} className="border border-gray-200 rounded-lg p-4">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center space-x-3">
                  <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center">
                    <User size={16} className="text-blue-600" />
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900">{person.name}</h3>
                    <p className="text-sm text-gray-500">{person.vehicleType}</p>
                  </div>
                </div>
                <div className="flex items-center">
                  {getStatusIcon(person.status)}
                  <span className={`ml-1 px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(person.status)}`}>
                    {person.status}
                  </span>
                </div>
              </div>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Deliveries:</span>
                  <span className="font-medium">{person.totalDeliveries}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Rating:</span>
                  <span className="font-medium">{person.rating} ⭐</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Current:</span>
                  <span className="font-medium">{person.currentAssignments} orders</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Delivery Assignments */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-gray-900">Delivery Assignments</h2>
          <div className="flex items-center space-x-4">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
              <input
                type="text"
                placeholder="Search assignments..."
                className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
              <option>All Status</option>
              <option>Assigned</option>
              <option>In Transit</option>
              <option>Delivered</option>
              <option>Failed</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Order
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Delivery Person
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Customer
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Delivery Time
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Distance
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {deliveryAssignments.map((assignment) => (
                <tr key={assignment.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">{assignment.orderId}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-900">{assignment.deliveryPersonnel}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div>
                      <div className="text-sm font-medium text-gray-900">{assignment.customer}</div>
                      <div className="text-sm text-gray-500 flex items-center">
                        <MapPin size={12} className="mr-1" />
                        {assignment.address}
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      {getStatusIcon(assignment.status)}
                      <span className={`ml-2 px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(assignment.status)}`}>
                        {assignment.status}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-900">
                      {assignment.actualDelivery ? (
                        <div>
                          <div className="font-medium text-green-600">Delivered</div>
                          <div className="text-xs text-gray-500">{assignment.actualDelivery}</div>
                        </div>
                      ) : (
                        <div>
                          <div className="font-medium">ETA: {assignment.estimatedDelivery}</div>
                          <div className="text-xs text-gray-500">Assigned: {assignment.assignedAt}</div>
                        </div>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {assignment.distance}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <button
                      onClick={() => setSelectedAssignment(assignment)}
                      className="text-blue-600 hover:text-blue-900"
                    >
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Assignment Detail Modal */}
      {selectedAssignment && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-bold text-gray-900">Assignment Details</h2>
              <button
                onClick={() => setSelectedAssignment(null)}
                className="text-gray-500 hover:text-gray-700"
              >
                <XCircle size={24} />
              </button>
            </div>
            
            <div className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Order ID</label>
                  <p className="text-lg font-semibold text-gray-900">{selectedAssignment.orderId}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                  <div className="flex items-center">
                    {getStatusIcon(selectedAssignment.status)}
                    <span className={`ml-2 px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(selectedAssignment.status)}`}>
                      {selectedAssignment.status}
                    </span>
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Delivery Personnel</label>
                  <p className="text-gray-900">{selectedAssignment.deliveryPersonnel}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Customer</label>
                  <p className="text-gray-900">{selectedAssignment.customer}</p>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Delivery Address</label>
                <p className="text-gray-900">{selectedAssignment.address}</p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Estimated Delivery</label>
                  <p className="text-gray-900">{selectedAssignment.estimatedDelivery}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Actual Delivery</label>
                  <p className="text-gray-900">{selectedAssignment.actualDelivery || 'Not delivered yet'}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Distance</label>
                  <p className="text-gray-900">{selectedAssignment.distance}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Assigned At</label>
                  <p className="text-gray-900">{selectedAssignment.assignedAt}</p>
                </div>
              </div>

              <div className="flex items-center justify-end space-x-4 pt-6 border-t">
                <button
                  onClick={() => setSelectedAssignment(null)}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Close
                </button>
                {selectedAssignment.status === 'Assigned' && (
                  <button className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
                    Reassign
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};