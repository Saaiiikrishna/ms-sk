import React, { useState } from 'react';
import { Bell, User, LogOut, Search, X, CheckCircle, AlertTriangle, Info, Clock } from 'lucide-react';
import { PageType } from '../../App';

interface HeaderProps {
  onLogout: () => void;
  currentPage: PageType;
  onPageChange: (page: PageType) => void;
}

interface Notification {
  id: number;
  type: 'info' | 'warning' | 'success' | 'error';
  title: string;
  message: string;
  time: string;
  read: boolean;
  actionLabel?: string;
  onAction?: () => void;
}

interface SearchSuggestion {
  id: number;
  text: string;
  type: 'user' | 'vendor' | 'product' | 'order';
  category: string;
}

export const Header: React.FC<HeaderProps> = ({ onLogout, currentPage, onPageChange }) => {
  const [showNotifications, setShowNotifications] = useState(false);
  const [showSearchSuggestions, setShowSearchSuggestions] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  
  const [searchHistory] = useState<string[]>([
    'iPhone 15 Pro',
    'john.doe@email.com',
    'TechStore Inc.',
    'Order #ORD-2024-001',
    'Samsung Galaxy'
  ]);

  const [searchSuggestions] = useState<SearchSuggestion[]>([
    { id: 1, text: 'iPhone 15 Pro Max', type: 'product', category: 'Products' },
    { id: 2, text: 'john.smith@email.com', type: 'user', category: 'Users' },
    { id: 3, text: 'TechStore Inc.', type: 'vendor', category: 'Vendors' },
    { id: 4, text: 'Order #ORD-2024-005', type: 'order', category: 'Orders' },
    { id: 5, text: 'Samsung Galaxy S24 Ultra', type: 'product', category: 'Products' },
    { id: 6, text: 'jane.wilson@vendor.com', type: 'vendor', category: 'Vendors' }
  ]);

  const [notifications, setNotifications] = useState<Notification[]>([
    {
      id: 1,
      type: 'warning',
      title: 'KYC Review Required',
      message: 'Fashion Hub has submitted new KYC documents for review',
      time: '5 minutes ago',
      read: false,
      actionLabel: 'Review',
      onAction: () => onPageChange('vendors')
    },
    {
      id: 2,
      type: 'error',
      title: 'Low Stock Alert',
      message: 'Samsung Galaxy S24 is running low on stock (8 units remaining)',
      time: '15 minutes ago',
      read: false,
      actionLabel: 'Restock',
      onAction: () => onPageChange('inventory')
    },
    {
      id: 3,
      type: 'success',
      title: 'New User Registration',
      message: 'john.doe@email.com has successfully registered',
      time: '30 minutes ago',
      read: false,
      actionLabel: 'View User',
      onAction: () => onPageChange('users')
    },
    {
      id: 4,
      type: 'info',
      title: 'Support Ticket Resolved',
      message: 'Ticket #SUP-2024-456 has been marked as resolved',
      time: '1 hour ago',
      read: true,
      actionLabel: 'View Ticket',
      onAction: () => onPageChange('support')
    },
    {
      id: 5,
      type: 'warning',
      title: 'Delivery Delay',
      message: 'Order #ORD-2024-003 delivery has been delayed due to traffic',
      time: '2 hours ago',
      read: true,
      actionLabel: 'Track',
      onAction: () => onPageChange('delivery')
    }
  ]);

  const unreadCount = notifications.filter(n => !n.read).length;

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case 'success':
        return <CheckCircle size={16} className="text-green-500" />;
      case 'warning':
        return <AlertTriangle size={16} className="text-yellow-500" />;
      case 'error':
        return <AlertTriangle size={16} className="text-red-500" />;
      case 'info':
      default:
        return <Info size={16} className="text-blue-500" />;
    }
  };

  const getNotificationBorderColor = (type: string) => {
    switch (type) {
      case 'success':
        return 'border-l-green-500';
      case 'warning':
        return 'border-l-yellow-500';
      case 'error':
        return 'border-l-red-500';
      case 'info':
      default:
        return 'border-l-blue-500';
    }
  };

  const markAsRead = (id: number) => {
    setNotifications(prev => 
      prev.map(notification => 
        notification.id === id 
          ? { ...notification, read: true }
          : notification
      )
    );
  };

  const markAllAsRead = () => {
    setNotifications(prev => 
      prev.map(notification => ({ ...notification, read: true }))
    );
  };

  const handleNotificationAction = (notification: Notification) => {
    if (notification.onAction) {
      notification.onAction();
    }
    markAsRead(notification.id);
    setShowNotifications(false);
  };

  const handleSearchFocus = () => {
    setShowSearchSuggestions(true);
  };

  const handleSearchBlur = () => {
    // Delay hiding to allow clicking on suggestions
    setTimeout(() => setShowSearchSuggestions(false), 200);
  };

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
    setShowSearchSuggestions(true);
  };

  const handleSearchSelect = (text: string) => {
    setSearchQuery(text);
    setShowSearchSuggestions(false);
    // Here you would typically perform the search
    console.log('Searching for:', text);
  };

  const handleLogoClick = () => {
    if (currentPage !== 'dashboard') {
      onPageChange('dashboard');
    }
  };

  const handleProfileClick = () => {
    // Navigate to admin profile - for now we'll use settings page
    onPageChange('settings');
  };

  // Handle navigation for create admin
  const handleCreateAdminNavigation = () => {
    onPageChange('create-admin');
  };

  const filteredSuggestions = searchQuery 
    ? searchSuggestions.filter(suggestion => 
        suggestion.text.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : [];

  const showHistory = !searchQuery && searchHistory.length > 0;
  const showSuggestions = searchQuery && filteredSuggestions.length > 0;

  return (
    <header className="bg-white border-b border-gray-200 px-6 py-4 relative">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
            <input
              type="text"
              placeholder="Search users, vendors, products..."
              value={searchQuery}
              onChange={handleSearchChange}
              onFocus={handleSearchFocus}
              onBlur={handleSearchBlur}
              className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent w-80"
            />
            
            {/* Search Suggestions Dropdown */}
            {showSearchSuggestions && (showHistory || showSuggestions) && (
              <div className="absolute top-full left-0 right-0 mt-1 bg-white rounded-lg shadow-lg border border-gray-200 z-50 max-h-64 overflow-y-auto">
                {showHistory && (
                  <div className="p-2">
                    <div className="text-xs font-medium text-gray-500 px-3 py-2">Recent Searches</div>
                    {searchHistory.map((item, index) => (
                      <button
                        key={index}
                        onClick={() => handleSearchSelect(item)}
                        className="w-full text-left px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded flex items-center space-x-2"
                      >
                        <Clock size={14} className="text-gray-400" />
                        <span>{item}</span>
                      </button>
                    ))}
                  </div>
                )}
                
                {showSuggestions && (
                  <div className="p-2">
                    <div className="text-xs font-medium text-gray-500 px-3 py-2">Suggestions</div>
                    {filteredSuggestions.map((suggestion) => (
                      <button
                        key={suggestion.id}
                        onClick={() => handleSearchSelect(suggestion.text)}
                        className="w-full text-left px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded flex items-center justify-between"
                      >
                        <span>{suggestion.text}</span>
                        <span className="text-xs text-gray-400">{suggestion.category}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="flex items-center space-x-4">
          <div className="relative">
            <button 
              onClick={() => setShowNotifications(!showNotifications)}
              className="relative p-2 text-gray-500 hover:text-gray-700 transition-colors rounded-lg hover:bg-gray-100"
            >
              <Bell size={20} />
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center font-medium">
                  {unreadCount}
                </span>
              )}
            </button>

            {/* Notifications Dropdown */}
            {showNotifications && (
              <div className="absolute right-0 top-full mt-2 w-96 bg-white rounded-xl shadow-2xl border border-gray-200 z-50 max-h-96 overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between p-4 border-b border-gray-200">
                  <div className="flex items-center space-x-2">
                    <Bell size={18} className="text-gray-600" />
                    <h3 className="font-semibold text-gray-900">Notifications</h3>
                    {unreadCount > 0 && (
                      <span className="bg-red-100 text-red-600 text-xs px-2 py-1 rounded-full font-medium">
                        {unreadCount} new
                      </span>
                    )}
                  </div>
                  <button
                    onClick={() => setShowNotifications(false)}
                    className="p-1 text-gray-400 hover:text-gray-600 transition-colors rounded"
                  >
                    <X size={16} />
                  </button>
                </div>

                {/* Mark all as read button */}
                {unreadCount > 0 && (
                  <div className="p-3 border-b border-gray-100">
                    <button
                      onClick={markAllAsRead}
                      className="text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors"
                    >
                      Mark all as read
                    </button>
                  </div>
                )}

                {/* Notifications List */}
                <div className="max-h-80 overflow-y-auto">
                  {notifications.length === 0 ? (
                    <div className="p-6 text-center text-gray-500">
                      <Bell size={32} className="mx-auto mb-2 text-gray-300" />
                      <p>No notifications</p>
                    </div>
                  ) : (
                    <div className="divide-y divide-gray-100">
                      {notifications.map((notification) => (
                        <div
                          key={notification.id}
                          className={`p-4 hover:bg-gray-50 transition-colors border-l-4 ${getNotificationBorderColor(notification.type)} ${
                            !notification.read ? 'bg-blue-50' : ''
                          }`}
                        >
                          <div className="flex items-start space-x-3">
                            <div className="flex-shrink-0 mt-1">
                              {getNotificationIcon(notification.type)}
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-start justify-between">
                                <div className="flex-1">
                                  <p className={`text-sm font-medium ${!notification.read ? 'text-gray-900' : 'text-gray-700'}`}>
                                    {notification.title}
                                  </p>
                                  <p className="text-sm text-gray-600 mt-1">
                                    {notification.message}
                                  </p>
                                  <div className="flex items-center justify-between mt-2">
                                    <div className="flex items-center text-xs text-gray-500">
                                      <Clock size={12} className="mr-1" />
                                      {notification.time}
                                    </div>
                                    {!notification.read && (
                                      <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
                                    )}
                                  </div>
                                </div>
                              </div>
                              {notification.actionLabel && (
                                <div className="mt-3">
                                  <button
                                    onClick={() => handleNotificationAction(notification)}
                                    className="text-xs bg-blue-600 text-white px-3 py-1 rounded-md hover:bg-blue-700 transition-colors font-medium"
                                  >
                                    {notification.actionLabel}
                                  </button>
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Footer */}
                {notifications.length > 0 && (
                  <div className="p-3 border-t border-gray-200 bg-gray-50">
                    <button className="w-full text-sm text-blue-600 hover:text-blue-700 font-medium transition-colors">
                      View all notifications
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>

          <button
            onClick={handleProfileClick}
            className="flex items-center space-x-3 hover:bg-gray-100 rounded-lg p-2 transition-colors"
          >
            <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center">
              <User size={16} className="text-white" />
            </div>
            <div className="hidden md:block text-left">
              <p className="text-sm font-medium text-gray-900">Admin User</p>
              <p className="text-xs text-gray-500">Super Administrator</p>
            </div>
          </button>

          <button
            onClick={onLogout}
            className="p-2 text-gray-500 hover:text-red-600 transition-colors rounded-lg hover:bg-gray-100"
            title="Logout"
          >
            <LogOut size={20} />
          </button>
        </div>
      </div>
    </header>
  );
};