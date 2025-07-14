import React, { useState } from 'react';
import { ShoppingCart, Search, Filter, Plus, Edit, Trash2, Eye, Tag, Package } from 'lucide-react';

export const CatalogManagement: React.FC = () => {
  const [selectedProduct, setSelectedProduct] = useState<any>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [activeTab, setActiveTab] = useState<'products' | 'categories'>('products');

  const products = [
    {
      id: 1,
      name: 'iPhone 15 Pro',
      sku: 'IPH-15P-128-BLK',
      category: 'Electronics > Smartphones',
      price: 999,
      stock: 45,
      status: 'Active',
      vendor: 'TechStore Inc.',
      rating: 4.8,
      reviews: 234,
      description: 'Latest iPhone with advanced camera system and titanium design',
      images: ['https://images.pexels.com/photos/699122/pexels-photo-699122.jpeg']
    },
    {
      id: 2,
      name: 'Samsung Galaxy S24',
      sku: 'SAM-S24-256-WHT',
      category: 'Electronics > Smartphones',
      price: 899,
      stock: 8,
      status: 'Active',
      vendor: 'TechStore Inc.',
      rating: 4.6,
      reviews: 189,
      description: 'Powerful Android smartphone with AI-enhanced features',
      images: ['https://images.pexels.com/photos/699122/pexels-photo-699122.jpeg']
    },
    {
      id: 3,
      name: 'Nike Air Max 270',
      sku: 'NIK-AM270-42-BLK',
      category: 'Fashion > Footwear',
      price: 150,
      stock: 0,
      status: 'Inactive',
      vendor: 'Fashion Hub',
      rating: 4.4,
      reviews: 156,
      description: 'Comfortable running shoes with Air Max technology',
      images: ['https://images.pexels.com/photos/2529148/pexels-photo-2529148.jpeg']
    },
    {
      id: 4,
      name: 'Coffee Maker Deluxe',
      sku: 'CFM-DLX-001',
      category: 'Home & Garden > Kitchen',
      price: 89,
      stock: 32,
      status: 'Active',
      vendor: 'Home Essentials',
      rating: 4.2,
      reviews: 87,
      description: 'Professional-grade coffee maker with programmable features',
      images: ['https://images.pexels.com/photos/324028/pexels-photo-324028.jpeg']
    }
  ];

  const categories = [
    {
      id: 1,
      name: 'Electronics',
      description: 'Electronic devices and accessories',
      productCount: 156,
      subcategories: ['Smartphones', 'Laptops', 'Tablets', 'Accessories']
    },
    {
      id: 2,
      name: 'Fashion',
      description: 'Clothing and fashion accessories',
      productCount: 234,
      subcategories: ['Footwear', 'Clothing', 'Accessories']
    },
    {
      id: 3,
      name: 'Home & Garden',
      description: 'Home improvement and garden supplies',
      productCount: 89,
      subcategories: ['Kitchen', 'Bathroom', 'Garden', 'Furniture']
    }
  ];

  const getStatusColor = (status: string) => {
    return status === 'Active' ? 'text-green-600 bg-green-100' : 'text-red-600 bg-red-100';
  };

  const getStockStatusColor = (stock: number) => {
    if (stock === 0) return 'text-red-600';
    if (stock < 10) return 'text-yellow-600';
    return 'text-green-600';
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Catalog Management</h1>
          <p className="text-gray-600">Manage products and categories</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2"
        >
          <Plus size={20} />
          <span>Add Product</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => setActiveTab('products')}
            className={`py-2 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'products'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Products
          </button>
          <button
            onClick={() => setActiveTab('categories')}
            className={`py-2 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'categories'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Categories
          </button>
        </nav>
      </div>

      {activeTab === 'products' ? (
        <>
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
                <option>Fashion</option>
                <option>Home & Garden</option>
              </select>
              <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
                <option>All Status</option>
                <option>Active</option>
                <option>Inactive</option>
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

          {/* Products Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {products.map((product) => (
              <div key={product.id} className="bg-white rounded-xl border border-gray-200 overflow-hidden hover:shadow-lg transition-shadow">
                <div className="aspect-square bg-gray-100 flex items-center justify-center">
                  <img
                    src={product.images[0]}
                    alt={product.name}
                    className="w-full h-full object-cover"
                  />
                </div>
                <div className="p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className={`px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(product.status)}`}>
                      {product.status}
                    </span>
                    <span className={`text-sm font-medium ${getStockStatusColor(product.stock)}`}>
                      {product.stock} in stock
                    </span>
                  </div>
                  <h3 className="font-semibold text-gray-900 mb-2">{product.name}</h3>
                  <p className="text-sm text-gray-500 mb-2">{product.category}</p>
                  <p className="text-lg font-bold text-gray-900 mb-2">${product.price}</p>
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center">
                      <span className="text-sm text-gray-900">{product.rating}</span>
                      <div className="ml-1 flex text-yellow-400">
                        {'★'.repeat(Math.floor(product.rating))}
                      </div>
                      <span className="text-sm text-gray-500 ml-1">({product.reviews})</span>
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setSelectedProduct(product)}
                      className="flex-1 bg-blue-600 text-white px-3 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
                    >
                      View Details
                    </button>
                    <button className="p-2 text-gray-500 hover:text-blue-600 transition-colors">
                      <Edit size={16} />
                    </button>
                    <button className="p-2 text-gray-500 hover:text-red-600 transition-colors">
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      ) : (
        <>
          {/* Categories */}
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-gray-900">Categories</h2>
              <button className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2">
                <Plus size={16} />
                <span>Add Category</span>
              </button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {categories.map((category) => (
                <div key={category.id} className="border border-gray-200 rounded-lg p-6">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center space-x-3">
                      <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
                        <Tag size={20} className="text-blue-600" />
                      </div>
                      <div>
                        <h3 className="font-semibold text-gray-900">{category.name}</h3>
                        <p className="text-sm text-gray-500">{category.productCount} products</p>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <button className="p-1 text-gray-500 hover:text-blue-600 transition-colors">
                        <Edit size={16} />
                      </button>
                      <button className="p-1 text-gray-500 hover:text-red-600 transition-colors">
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                  <p className="text-gray-600 mb-4">{category.description}</p>
                  <div className="space-y-2">
                    <p className="text-sm font-medium text-gray-700">Subcategories:</p>
                    <div className="flex flex-wrap gap-2">
                      {category.subcategories.map((sub, index) => (
                        <span key={index} className="px-2 py-1 bg-gray-100 text-gray-700 text-xs rounded-full">
                          {sub}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* Product Detail Modal */}
      {selectedProduct && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-4xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-bold text-gray-900">Product Details</h2>
              <button
                onClick={() => setSelectedProduct(null)}
                className="text-gray-500 hover:text-gray-700"
              >
                <Eye size={24} />
              </button>
            </div>
            
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
              <div>
                <img
                  src={selectedProduct.images[0]}
                  alt={selectedProduct.name}
                  className="w-full h-80 object-cover rounded-lg bg-gray-100"
                />
              </div>
              <div className="space-y-6">
                <div>
                  <h3 className="text-2xl font-bold text-gray-900 mb-2">{selectedProduct.name}</h3>
                  <p className="text-gray-600 mb-4">{selectedProduct.description}</p>
                  <div className="flex items-center space-x-4 mb-4">
                    <span className="text-3xl font-bold text-gray-900">${selectedProduct.price}</span>
                    <span className={`px-3 py-1 text-sm font-medium rounded-full ${getStatusColor(selectedProduct.status)}`}>
                      {selectedProduct.status}
                    </span>
                  </div>
                  <div className="flex items-center mb-4">
                    <span className="text-lg text-gray-900">{selectedProduct.rating}</span>
                    <div className="ml-2 flex text-yellow-400">
                      {'★'.repeat(Math.floor(selectedProduct.rating))}
                    </div>
                    <span className="text-gray-500 ml-2">({selectedProduct.reviews} reviews)</span>
                  </div>
                </div>
                
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">SKU</label>
                    <p className="text-gray-900 font-mono">{selectedProduct.sku}</p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                    <p className="text-gray-900">{selectedProduct.category}</p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Stock</label>
                    <p className={`font-medium ${getStockStatusColor(selectedProduct.stock)}`}>
                      {selectedProduct.stock} units
                    </p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Vendor</label>
                    <p className="text-gray-900">{selectedProduct.vendor}</p>
                  </div>
                </div>
                
                <div className="flex items-center space-x-4 pt-6 border-t">
                  <button className="flex-1 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors">
                    Edit Product
                  </button>
                  <button className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
                    Duplicate
                  </button>
                  <button className="px-4 py-2 border border-red-300 text-red-600 rounded-lg hover:bg-red-50 transition-colors">
                    Delete
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};