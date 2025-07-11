import React, { useState } from 'react';
import { Package, Search, Filter, AlertTriangle, TrendingUp, TrendingDown, Plus, Minus } from 'lucide-react';

export const InventoryManagement: React.FC = () => {
  const [selectedItem, setSelectedItem] = useState<any>(null);

  const inventoryItems = [
    {
      id: 1,
      name: 'iPhone 15 Pro',
      sku: 'IPH-15P-128-BLK',
      category: 'Electronics',
      vendor: 'TechStore Inc.',
      currentStock: 45,
      minStock: 20,
      maxStock: 100,
      unitPrice: 999,
      totalValue: 44955,
      status: 'In Stock',
      lastUpdated: '2024-01-15',
      reorderLevel: 25
    },
    {
      id: 2,
      name: 'Samsung Galaxy S24',
      sku: 'SAM-S24-256-WHT',
      category: 'Electronics',
      vendor: 'TechStore Inc.',
      currentStock: 8,
      minStock: 15,
      maxStock: 80,
      unitPrice: 899,
      totalValue: 7192,
      status: 'Low Stock',
      lastUpdated: '2024-01-14',
      reorderLevel: 20
    },
    {
      id: 3,
      name: 'Nike Air Max 270',
      sku: 'NIK-AM270-42-BLK',
      category: 'Footwear',
      vendor: 'Fashion Hub',
      currentStock: 0,
      minStock: 10,
      maxStock: 50,
      unitPrice: 150,
      totalValue: 0,
      status: 'Out of Stock',
      lastUpdated: '2024-01-13',
      reorderLevel: 15
    },
    {
      id: 4,
      name: 'Coffee Maker Deluxe',
      sku: 'CFM-DLX-001',
      category: 'Home & Garden',
      vendor: 'Home Essentials',
      currentStock: 32,
      minStock: 5,
      maxStock: 40,
      unitPrice: 89,
      totalValue: 2848,
      status: 'In Stock',
      lastUpdated: '2024-01-15',
      reorderLevel: 8
    }
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'In Stock': return 'text-green-600 bg-green-100';
      case 'Low Stock': return 'text-yellow-600 bg-yellow-100';
      case 'Out of Stock': return 'text-red-600 bg-red-100';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'In Stock': return <Package size={16} className="text-green-600" />;
      case 'Low Stock': return <AlertTriangle size={16} className="text-yellow-600" />;
      case 'Out of Stock': return <AlertTriangle size={16} className="text-red-600" />;
      default: return <Package size={16} className="text-gray-600" />;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Inventory Management</h1>
          <p className="text-gray-600">Monitor and manage product inventory levels</p>
        </div>
        <div className="flex items-center space-x-4">
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <div className="flex items-center space-x-6">
              <div className="text-center">
                <div className="text-2xl font-bold text-green-600">2</div>
                <div className="text-sm text-gray-500">In Stock</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold text-yellow-600">1</div>
                <div className="text-sm text-gray-500">Low Stock</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold text-red-600">1</div>
                <div className="text-sm text-gray-500">Out of Stock</div>
              </div>
            </div>
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
                placeholder="Search products..."
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>
          <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
            <option>All Categories</option>
            <option>Electronics</option>
            <option>Footwear</option>
            <option>Home & Garden</option>
          </select>
          <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
            <option>All Status</option>
            <option>In Stock</option>
            <option>Low Stock</option>
            <option>Out of Stock</option>
          </select>
          <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
            <option>All Vendors</option>
            <option>TechStore Inc.</option>
            <option>Fashion Hub</option>
            <option>Home Essentials</option>
          </select>
          <button className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors flex items-center space-x-2">
            <Filter size={16} />
            <span>More Filters</span>
          </button>
        </div>
      </div>

      {/* Inventory Table */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Product
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  SKU
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Stock Level
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Unit Price
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Total Value
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {inventoryItems.map((item) => (
                <tr key={item.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center">
                        <Package size={16} className="text-gray-600" />
                      </div>
                      <div className="ml-4">
                        <div className="text-sm font-medium text-gray-900">{item.name}</div>
                        <div className="text-sm text-gray-500">{item.category}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 font-mono">
                    {item.sku}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      <div className="text-sm font-medium text-gray-900">{item.currentStock}</div>
                      <div className="ml-2 text-xs text-gray-500">
                        / {item.maxStock}
                      </div>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-2 mt-1">
                      <div
                        className={`h-2 rounded-full ${
                          item.currentStock === 0 ? 'bg-red-500' :
                          item.currentStock <= item.minStock ? 'bg-yellow-500' :
                          'bg-green-500'
                        }`}
                        style={{ width: `${Math.min((item.currentStock / item.maxStock) * 100, 100)}%` }}
                      ></div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      {getStatusIcon(item.status)}
                      <span className={`ml-2 px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(item.status)}`}>
                        {item.status}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 font-medium">
                    ${item.unitPrice}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 font-medium">
                    ${item.totalValue.toLocaleString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex items-center space-x-2">
                      <button className="text-blue-600 hover:text-blue-900 p-1 rounded">
                        <Plus size={16} />
                      </button>
                      <button className="text-red-600 hover:text-red-900 p-1 rounded">
                        <Minus size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Stock Adjustment Log */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Recent Stock Adjustments</h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center space-x-3">
              <TrendingUp className="text-green-600" size={20} />
              <div>
                <p className="text-sm font-medium text-gray-900">Stock Added: iPhone 15 Pro</p>
                <p className="text-xs text-gray-500">Added 20 units • 2 hours ago</p>
              </div>
            </div>
            <span className="text-sm text-green-600 font-medium">+20</span>
          </div>
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center space-x-3">
              <TrendingDown className="text-red-600" size={20} />
              <div>
                <p className="text-sm font-medium text-gray-900">Stock Sold: Samsung Galaxy S24</p>
                <p className="text-xs text-gray-500">Order fulfillment • 4 hours ago</p>
              </div>
            </div>
            <span className="text-sm text-red-600 font-medium">-5</span>
          </div>
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center space-x-3">
              <AlertTriangle className="text-yellow-600" size={20} />
              <div>
                <p className="text-sm font-medium text-gray-900">Low Stock Alert: Nike Air Max 270</p>
                <p className="text-xs text-gray-500">Below minimum threshold • 6 hours ago</p>
              </div>
            </div>
            <span className="text-sm text-yellow-600 font-medium">Alert</span>
          </div>
        </div>
      </div>
    </div>
  );
};