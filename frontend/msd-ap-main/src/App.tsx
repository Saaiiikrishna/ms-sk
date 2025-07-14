import React, { useState } from 'react';
import { Sidebar } from './components/layout/Sidebar';
import { Header } from './components/layout/Header';
import { Dashboard } from './components/pages/Dashboard';
import { UserManagement } from './components/pages/UserManagement';
import { VendorManagement } from './components/pages/VendorManagement';
import { InventoryManagement } from './components/pages/InventoryManagement';
import { DeliveryManagement } from './components/pages/DeliveryManagement';
import { SupportManagement } from './components/pages/SupportManagement';
import { CatalogManagement } from './components/pages/CatalogManagement';
import { Settings } from './components/pages/Settings';
import { LoginPage } from './components/auth/LoginPage';
import { AdminManagement } from './components/pages/AdminManagement';
import { CreateAdmin } from './components/pages/CreateAdmin';
import { UpdateAdmin } from './components/pages/UpdateAdmin';

export type PageType = 'dashboard' | 'users' | 'vendors' | 'inventory' | 'delivery' | 'support' | 'catalog' | 'settings' | 'admins' | 'create-admin' | 'update-admin';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentPage, setCurrentPage] = useState<PageType>('dashboard');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [selectedAdminId, setSelectedAdminId] = useState<string>('');

  const handleLogin = () => {
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setCurrentPage('dashboard');
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard':
        return <Dashboard />;
      case 'users':
        return <UserManagement />;
      case 'vendors':
        return <VendorManagement />;
      case 'inventory':
        return <InventoryManagement />;
      case 'delivery':
        return <DeliveryManagement />;
      case 'support':
        return <SupportManagement />;
      case 'catalog':
        return <CatalogManagement />;
      case 'settings':
        return <Settings onPageChange={setCurrentPage} />;
      case 'admins':
        return <AdminManagement onPageChange={setCurrentPage} onSelectAdmin={setSelectedAdminId} />;
      case 'create-admin':
        return <CreateAdmin onPageChange={setCurrentPage} />;
      case 'update-admin':
        return <UpdateAdmin adminId={selectedAdminId} onPageChange={setCurrentPage} />;
      default:
        return <Dashboard />;
    }
  };

  if (!isAuthenticated) {
    return <LoginPage onLogin={handleLogin} />;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex">
      <Sidebar
        currentPage={currentPage}
        onPageChange={setCurrentPage}
        collapsed={sidebarCollapsed}
        onToggleCollapse={() => setSidebarCollapsed(!sidebarCollapsed)}
      />
      <div className={`flex-1 transition-all duration-300 ${sidebarCollapsed ? 'ml-16' : 'ml-64'}`}>
        <Header 
          onLogout={handleLogout}
          currentPage={currentPage}
          onPageChange={setCurrentPage}
        />
        <main className="p-6">
          {renderPage()}
        </main>
      </div>
    </div>
  );
}

export default App;