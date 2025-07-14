import React from 'react';
import { 
  LayoutDashboard, 
  Users, 
  Store, 
  Package, 
  Truck, 
  MessageSquare, 
  ShoppingCart, 
  Settings,
  ChevronLeft,
  ChevronRight,
  Shield
} from 'lucide-react';
import { PageType } from '../../App';

interface SidebarProps {
  currentPage: PageType;
  onPageChange: (page: PageType) => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentPage,
  onPageChange,
  collapsed,
  onToggleCollapse
}) => {
  const menuItems = [
    { id: 'dashboard' as PageType, icon: LayoutDashboard, label: 'Dashboard' },
    { id: 'users' as PageType, icon: Users, label: 'User Management' },
    { id: 'vendors' as PageType, icon: Store, label: 'Vendor Management' },
    { id: 'inventory' as PageType, icon: Package, label: 'Inventory' },
    { id: 'delivery' as PageType, icon: Truck, label: 'Delivery' },
    { id: 'support' as PageType, icon: MessageSquare, label: 'Support' },
    { id: 'catalog' as PageType, icon: ShoppingCart, label: 'Catalog' },
    { id: 'settings' as PageType, icon: Settings, label: 'Settings' },
  ];

  const handleLogoClick = () => {
    if (currentPage !== 'dashboard') {
      onPageChange('dashboard');
    }
  };

  return (
    <div className={`fixed left-0 top-0 h-full bg-gray-900 text-white transition-all duration-300 z-50 ${
      collapsed ? 'w-16' : 'w-64'
    }`}>
      <div className="p-4 border-b border-gray-800">
        <div className="flex items-center justify-between">
          {!collapsed && (
            <button
              onClick={handleLogoClick}
              className="flex items-center space-x-2 hover:bg-gray-800 rounded-lg p-2 transition-colors"
            >
              <Shield className="w-8 h-8 text-blue-400" />
              <div>
                <h1 className="text-lg font-bold">MySillyDreams</h1>
                <p className="text-xs text-gray-400">Admin Panel</p>
              </div>
            </button>
          )}
          {collapsed && (
            <button
              onClick={handleLogoClick}
              className="w-full flex justify-center hover:bg-gray-800 rounded-lg p-2 transition-colors"
            >
              <Shield className="w-8 h-8 text-blue-400" />
            </button>
          )}
          <button
            onClick={onToggleCollapse}
            className="p-2 rounded-lg hover:bg-gray-800 transition-colors"
          >
            {collapsed ? <ChevronRight size={20} /> : <ChevronLeft size={20} />}
          </button>
        </div>
      </div>

      <nav className="flex-1 p-4">
        <ul className="space-y-2">
          {menuItems.map((item) => (
            <li key={item.id}>
              <button
                onClick={() => onPageChange(item.id)}
                className={`w-full flex items-center ${collapsed ? 'justify-center px-2 py-4' : 'space-x-3 px-3 py-3'} rounded-lg transition-colors ${
                  currentPage === item.id
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-300 hover:bg-gray-800 hover:text-white'
                }`}
                title={collapsed ? item.label : ''}
              >
                <item.icon className={collapsed ? 'w-7 h-7' : 'w-5 h-5'} />
                {!collapsed && <span className="font-medium">{item.label}</span>}
              </button>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
};