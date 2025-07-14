import React, { useState, useEffect } from 'react';
import { 
  Users, 
  Store, 
  Package, 
  Truck, 
  MessageSquare, 
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  RefreshCw
} from 'lucide-react';

interface DashboardStats {
  totalUsers: number;
  activeVendors: number;
  products: number;
  deliveriesToday: number;
  userChange: string;
  vendorChange: string;
  productChange: string;
  deliveryChange: string;
}

interface Activity {
  type: string;
  message: string;
  time: string;
}

interface Task {
  id: number;
  title: string;
  priority: 'high' | 'medium' | 'low';
  type: string;
}

export const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats>({
    totalUsers: 12456,
    activeVendors: 1234,
    products: 45678,
    deliveriesToday: 892,
    userChange: '+12%',
    vendorChange: '+8%',
    productChange: '+15%',
    deliveryChange: '+5%'
  });

  const [recentActivities, setRecentActivities] = useState<Activity[]>([
    { type: 'user', message: 'New user registration: john.doe@email.com', time: '2 minutes ago' },
    { type: 'vendor', message: 'Vendor KYC approved: TechStore Inc.', time: '15 minutes ago' },
    { type: 'product', message: 'Product inventory updated: iPhone 15', time: '30 minutes ago' },
    { type: 'delivery', message: 'Delivery completed: Order #ORD-2024-001', time: '1 hour ago' },
    { type: 'support', message: 'Support ticket resolved: #SUP-2024-456', time: '2 hours ago' },
  ]);

  const [pendingTasks, setPendingTasks] = useState<Task[]>([
    { id: 1, title: 'Review 12 vendor KYC applications', priority: 'high', type: 'vendor' },
    { id: 2, title: 'Approve 5 new product listings', priority: 'medium', type: 'product' },
    { id: 3, title: 'Resolve 8 open support tickets', priority: 'high', type: 'support' },
    { id: 4, title: 'Update inventory for 15 low-stock items', priority: 'low', type: 'inventory' },
  ]);

  const [isRefreshing, setIsRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(new Date());

  // Simulate real-time data updates
  useEffect(() => {
    const interval = setInterval(() => {
      // Simulate random data changes
      setStats(prevStats => ({
        ...prevStats,
        totalUsers: prevStats.totalUsers + Math.floor(Math.random() * 5),
        deliveriesToday: prevStats.deliveriesToday + Math.floor(Math.random() * 3),
      }));

      // Add new activities occasionally
      if (Math.random() > 0.7) {
        const newActivities = [
          'New user registration: user' + Math.floor(Math.random() * 1000) + '@email.com',
          'Order completed: #ORD-2024-' + Math.floor(Math.random() * 1000),
          'Product stock updated: Product #' + Math.floor(Math.random() * 100),
          'Support ticket created: #SUP-2024-' + Math.floor(Math.random() * 1000)
        ];
        
        const randomActivity = newActivities[Math.floor(Math.random() * newActivities.length)];
        
        setRecentActivities(prev => [
          { type: 'new', message: randomActivity, time: 'Just now' },
          ...prev.slice(0, 4)
        ]);
      }

      setLastUpdated(new Date());
    }, 30000); // Update every 30 seconds

    return () => clearInterval(interval);
  }, []);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    
    // Simulate API call delay
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // Simulate fresh data
    setStats(prevStats => ({
      ...prevStats,
      totalUsers: prevStats.totalUsers + Math.floor(Math.random() * 10),
      activeVendors: prevStats.activeVendors + Math.floor(Math.random() * 5),
      products: prevStats.products + Math.floor(Math.random() * 20),
      deliveriesToday: prevStats.deliveriesToday + Math.floor(Math.random() * 8),
    }));

    setLastUpdated(new Date());
    setIsRefreshing(false);
  };

  const statsData = [
    { 
      title: 'Total Users', 
      value: stats.totalUsers.toLocaleString(), 
      change: stats.userChange, 
      icon: Users, 
      color: 'blue' 
    },
    { 
      title: 'Active Vendors', 
      value: stats.activeVendors.toLocaleString(), 
      change: stats.vendorChange, 
      icon: Store, 
      color: 'green' 
    },
    { 
      title: 'Products', 
      value: stats.products.toLocaleString(), 
      change: stats.productChange, 
      icon: Package, 
      color: 'purple' 
    },
    { 
      title: 'Deliveries Today', 
      value: stats.deliveriesToday.toLocaleString(), 
      change: stats.deliveryChange, 
      icon: Truck, 
      color: 'orange' 
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-600">Welcome back! Here's what's happening on your platform.</p>
        </div>
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2 text-sm text-gray-500">
            <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
            <span>Live data</span>
          </div>
          <div className="text-xs text-gray-400">
            Last updated: {lastUpdated.toLocaleTimeString()}
          </div>
          <button
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="flex items-center space-x-2 px-3 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
          >
            <RefreshCw size={16} className={isRefreshing ? 'animate-spin' : ''} />
            <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {statsData.map((stat, index) => (
          <div key={index} className="bg-white rounded-xl p-6 border border-gray-200 hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1">
            <div className="flex items-center justify-between mb-4">
              <div className={`p-3 rounded-lg bg-${stat.color}-100`}>
                <stat.icon className={`w-6 h-6 text-${stat.color}-600`} />
              </div>
              <span className="text-sm font-medium text-green-600 flex items-center">
                <TrendingUp size={14} className="mr-1" />
                {stat.change}
              </span>
            </div>
            <h3 className="text-2xl font-bold text-gray-900 mb-1">{stat.value}</h3>
            <p className="text-gray-600">{stat.title}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Activities */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold text-gray-900">Recent Activities</h2>
            <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" title="Live updates"></div>
          </div>
          <div className="space-y-4 max-h-80 overflow-y-auto">
            {recentActivities.map((activity, index) => (
              <div key={index} className="flex items-start space-x-3 p-3 rounded-lg hover:bg-gray-50 transition-colors">
                <div className={`w-2 h-2 rounded-full mt-2 ${
                  activity.type === 'new' ? 'bg-green-500 animate-pulse' : 'bg-blue-500'
                }`}></div>
                <div className="flex-1">
                  <p className="text-gray-900 text-sm">{activity.message}</p>
                  <p className="text-gray-500 text-xs mt-1">{activity.time}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Pending Tasks */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-6">Pending Tasks</h2>
          <div className="space-y-4">
            {pendingTasks.map((task) => (
              <div key={task.id} className="flex items-center space-x-3 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                <div className={`w-3 h-3 rounded-full ${
                  task.priority === 'high' ? 'bg-red-500' : 
                  task.priority === 'medium' ? 'bg-yellow-500' : 'bg-green-500'
                }`}></div>
                <div className="flex-1">
                  <p className="text-gray-900 text-sm font-medium">{task.title}</p>
                  <p className="text-gray-500 text-xs">{task.priority} priority</p>
                </div>
                <button className="text-blue-600 hover:text-blue-800 text-sm font-medium px-3 py-1 rounded-md hover:bg-blue-50 transition-colors">
                  View
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* System Health */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-xl font-semibold text-gray-900 mb-6">System Health</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="text-center p-4 rounded-lg bg-green-50 border border-green-200">
            <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-3" />
            <h3 className="font-semibold text-gray-900">API Services</h3>
            <p className="text-sm text-green-600">All services operational</p>
            <div className="mt-2 text-xs text-gray-500">99.9% uptime</div>
          </div>
          <div className="text-center p-4 rounded-lg bg-green-50 border border-green-200">
            <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-3" />
            <h3 className="font-semibold text-gray-900">Database</h3>
            <p className="text-sm text-green-600">Performance optimal</p>
            <div className="mt-2 text-xs text-gray-500">Response time: 12ms</div>
          </div>
          <div className="text-center p-4 rounded-lg bg-yellow-50 border border-yellow-200">
            <AlertTriangle className="w-12 h-12 text-yellow-500 mx-auto mb-3" />
            <h3 className="font-semibold text-gray-900">Storage</h3>
            <p className="text-sm text-yellow-600">85% capacity used</p>
            <div className="mt-2 text-xs text-gray-500">425 GB / 500 GB</div>
          </div>
        </div>
      </div>
    </div>
  );
};