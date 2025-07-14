import React, { useState } from 'react';
import { MessageSquare, Search, Filter, Clock, CheckCircle, AlertCircle, User, Send } from 'lucide-react';

export const SupportManagement: React.FC = () => {
  const [selectedTicket, setSelectedTicket] = useState<any>(null);
  const [newMessage, setNewMessage] = useState('');

  const supportTickets = [
    {
      id: 1,
      ticketNumber: 'SUP-2024-001',
      customer: 'John Doe',
      customerEmail: 'john.doe@email.com',
      subject: 'Payment Processing Issue',
      description: 'Unable to complete payment for order ORD-2024-001',
      priority: 'High',
      status: 'Open',
      assignedAgent: 'Sarah Wilson',
      createdAt: '2024-01-15 10:30',
      updatedAt: '2024-01-15 14:20',
      messages: [
        {
          id: 1,
          sender: 'John Doe',
          message: 'Hello, I am having trouble completing my payment. The page keeps loading but nothing happens.',
          timestamp: '2024-01-15 10:30',
          isCustomer: true
        },
        {
          id: 2,
          sender: 'Sarah Wilson',
          message: 'Hi John, I apologize for the inconvenience. Can you please tell me which payment method you were trying to use?',
          timestamp: '2024-01-15 11:15',
          isCustomer: false
        },
        {
          id: 3,
          sender: 'John Doe',
          message: 'I was trying to use my credit card ending in 1234. It worked fine for my previous orders.',
          timestamp: '2024-01-15 14:20',
          isCustomer: true
        }
      ]
    },
    {
      id: 2,
      ticketNumber: 'SUP-2024-002',
      customer: 'Jane Smith',
      customerEmail: 'jane.smith@email.com',
      subject: 'Product Return Request',
      description: 'Requesting return for defective smartphone',
      priority: 'Medium',
      status: 'In Progress',
      assignedAgent: 'Mike Johnson',
      createdAt: '2024-01-14 15:45',
      updatedAt: '2024-01-15 09:10',
      messages: [
        {
          id: 1,
          sender: 'Jane Smith',
          message: 'I received my smartphone yesterday but it has a cracked screen. I would like to return it.',
          timestamp: '2024-01-14 15:45',
          isCustomer: true
        },
        {
          id: 2,
          sender: 'Mike Johnson',
          message: 'I\'m sorry to hear about this issue. I\'ll initiate the return process for you immediately.',
          timestamp: '2024-01-15 09:10',
          isCustomer: false
        }
      ]
    },
    {
      id: 3,
      ticketNumber: 'SUP-2024-003',
      customer: 'Bob Johnson',
      customerEmail: 'bob.johnson@email.com',
      subject: 'Account Access Issue',
      description: 'Cannot login to my account',
      priority: 'Low',
      status: 'Resolved',
      assignedAgent: 'Sarah Wilson',
      createdAt: '2024-01-13 12:20',
      updatedAt: '2024-01-13 16:30',
      messages: [
        {
          id: 1,
          sender: 'Bob Johnson',
          message: 'I forgot my password and the reset email is not coming through.',
          timestamp: '2024-01-13 12:20',
          isCustomer: true
        },
        {
          id: 2,
          sender: 'Sarah Wilson',
          message: 'I\'ve manually reset your password. Please check your email for the new temporary password.',
          timestamp: '2024-01-13 16:30',
          isCustomer: false
        }
      ]
    }
  ];

  const supportAgents = [
    { id: 1, name: 'Sarah Wilson', email: 'sarah.wilson@support.com', activeTickets: 8, totalResolved: 156 },
    { id: 2, name: 'Mike Johnson', email: 'mike.johnson@support.com', activeTickets: 5, totalResolved: 134 },
    { id: 3, name: 'Lisa Chen', email: 'lisa.chen@support.com', activeTickets: 3, totalResolved: 89 }
  ];

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'High': return 'text-red-600 bg-red-100';
      case 'Medium': return 'text-yellow-600 bg-yellow-100';
      case 'Low': return 'text-green-600 bg-green-100';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Open': return 'text-blue-600 bg-blue-100';
      case 'In Progress': return 'text-yellow-600 bg-yellow-100';
      case 'Resolved': return 'text-green-600 bg-green-100';
      case 'Closed': return 'text-gray-600 bg-gray-100';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'Open': return <AlertCircle size={16} className="text-blue-600" />;
      case 'In Progress': return <Clock size={16} className="text-yellow-600" />;
      case 'Resolved': return <CheckCircle size={16} className="text-green-600" />;
      case 'Closed': return <CheckCircle size={16} className="text-gray-600" />;
      default: return <AlertCircle size={16} className="text-gray-600" />;
    }
  };

  const handleSendMessage = () => {
    if (newMessage.trim() && selectedTicket) {
      const message = {
        id: selectedTicket.messages.length + 1,
        sender: 'Admin User',
        message: newMessage,
        timestamp: new Date().toISOString().slice(0, 16).replace('T', ' '),
        isCustomer: false
      };
      selectedTicket.messages.push(message);
      setNewMessage('');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Support Management</h1>
          <p className="text-gray-600">Manage support tickets and agents</p>
        </div>
        <div className="flex items-center space-x-4">
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <div className="flex items-center space-x-6">
              <div className="text-center">
                <div className="text-2xl font-bold text-blue-600">1</div>
                <div className="text-sm text-gray-500">Open</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold text-yellow-600">1</div>
                <div className="text-sm text-gray-500">In Progress</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold text-green-600">1</div>
                <div className="text-sm text-gray-500">Resolved</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Support Agents */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Support Agents</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {supportAgents.map((agent) => (
            <div key={agent.id} className="border border-gray-200 rounded-lg p-4">
              <div className="flex items-center space-x-3 mb-3">
                <div className="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center">
                  <User size={16} className="text-purple-600" />
                </div>
                <div>
                  <h3 className="font-medium text-gray-900">{agent.name}</h3>
                  <p className="text-sm text-gray-500">{agent.email}</p>
                </div>
              </div>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Active Tickets:</span>
                  <span className="font-medium">{agent.activeTickets}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Total Resolved:</span>
                  <span className="font-medium">{agent.totalResolved}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Support Tickets */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-semibold text-gray-900">Support Tickets</h2>
          <div className="flex items-center space-x-4">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
              <input
                type="text"
                placeholder="Search tickets..."
                className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
              <option>All Status</option>
              <option>Open</option>
              <option>In Progress</option>
              <option>Resolved</option>
              <option>Closed</option>
            </select>
            <select className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
              <option>All Priority</option>
              <option>High</option>
              <option>Medium</option>
              <option>Low</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Ticket
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Customer
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Subject
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Priority
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Assigned To
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {supportTickets.map((ticket) => (
                <tr key={ticket.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">{ticket.ticketNumber}</div>
                    <div className="text-sm text-gray-500">{ticket.createdAt}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">{ticket.customer}</div>
                    <div className="text-sm text-gray-500">{ticket.customerEmail}</div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-sm font-medium text-gray-900">{ticket.subject}</div>
                    <div className="text-sm text-gray-500">{ticket.description}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 py-1 text-xs font-medium rounded-full ${getPriorityColor(ticket.priority)}`}>
                      {ticket.priority}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      {getStatusIcon(ticket.status)}
                      <span className={`ml-2 px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(ticket.status)}`}>
                        {ticket.status}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {ticket.assignedAgent}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <button
                      onClick={() => setSelectedTicket(ticket)}
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

      {/* Ticket Detail Modal */}
      {selectedTicket && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between p-6 border-b">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">{selectedTicket.ticketNumber}</h2>
                <p className="text-gray-600">{selectedTicket.subject}</p>
              </div>
              <button
                onClick={() => setSelectedTicket(null)}
                className="text-gray-500 hover:text-gray-700"
              >
                <MessageSquare size={24} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Customer</label>
                  <p className="text-gray-900">{selectedTicket.customer}</p>
                  <p className="text-sm text-gray-500">{selectedTicket.customerEmail}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Assigned Agent</label>
                  <p className="text-gray-900">{selectedTicket.assignedAgent}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
                  <span className={`px-2 py-1 text-xs font-medium rounded-full ${getPriorityColor(selectedTicket.priority)}`}>
                    {selectedTicket.priority}
                  </span>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                  <div className="flex items-center">
                    {getStatusIcon(selectedTicket.status)}
                    <span className={`ml-2 px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(selectedTicket.status)}`}>
                      {selectedTicket.status}
                    </span>
                  </div>
                </div>
              </div>

              <div className="space-y-4">
                <h3 className="text-lg font-semibold text-gray-900">Conversation</h3>
                <div className="space-y-4 max-h-96 overflow-y-auto">
                  {selectedTicket.messages.map((message: any) => (
                    <div
                      key={message.id}
                      className={`flex ${message.isCustomer ? 'justify-start' : 'justify-end'}`}
                    >
                      <div
                        className={`max-w-xs lg:max-w-md px-4 py-2 rounded-lg ${
                          message.isCustomer
                            ? 'bg-gray-100 text-gray-900'
                            : 'bg-blue-600 text-white'
                        }`}
                      >
                        <p className="text-sm">{message.message}</p>
                        <p className={`text-xs mt-1 ${message.isCustomer ? 'text-gray-500' : 'text-blue-100'}`}>
                          {message.sender} • {message.timestamp}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="p-6 border-t">
              <div className="flex items-center space-x-4">
                <div className="flex-1">
                  <textarea
                    value={newMessage}
                    onChange={(e) => setNewMessage(e.target.value)}
                    placeholder="Type your response..."
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                    rows={3}
                  />
                </div>
                <button
                  onClick={handleSendMessage}
                  className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2"
                >
                  <Send size={16} />
                  <span>Send</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};