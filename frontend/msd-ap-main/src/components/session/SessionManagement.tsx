import React, { useState, useEffect } from 'react';
import { apiClient } from '../../services/api';

interface SessionInfo {
  id: string;
  token: string;
  issuedAt: string;
  expiresAt: string;
  ipAddress: string;
  userAgent: string;
  isCurrent: boolean;
  isValid: boolean;
}

interface SessionManagementProps {
  userId?: string;
}

/**
 * Session Management Component
 * Allows users to view and manage their active sessions
 */
export const SessionManagement: React.FC<SessionManagementProps> = ({ userId }) => {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revoking, setRevoking] = useState<Set<string>>(new Set());

  useEffect(() => {
    loadSessions();
  }, [userId]);

  const loadSessions = async () => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await apiClient.get('/api/auth/sessions');
      if (response.success && response.data) {
        setSessions(response.data.sessions || []);
      } else {
        setError('Failed to load sessions');
      }
    } catch (err) {
      setError('Error loading sessions');
      console.error('Session loading error:', err);
    } finally {
      setLoading(false);
    }
  };

  const revokeSession = async (sessionId: string) => {
    try {
      setRevoking(prev => new Set(prev).add(sessionId));
      
      const response = await apiClient.post(`/api/auth/sessions/${sessionId}/revoke`);
      if (response.success) {
        // Remove the revoked session from the list
        setSessions(prev => prev.filter(session => session.id !== sessionId));
      } else {
        setError('Failed to revoke session');
      }
    } catch (err) {
      setError('Error revoking session');
      console.error('Session revocation error:', err);
    } finally {
      setRevoking(prev => {
        const newSet = new Set(prev);
        newSet.delete(sessionId);
        return newSet;
      });
    }
  };

  const revokeAllOtherSessions = async () => {
    try {
      setLoading(true);
      
      const response = await apiClient.post('/api/auth/sessions/revoke-all-others');
      if (response.success) {
        // Reload sessions to show only current session
        await loadSessions();
      } else {
        setError('Failed to revoke other sessions');
      }
    } catch (err) {
      setError('Error revoking other sessions');
      console.error('Bulk session revocation error:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  const getBrowserInfo = (userAgent: string) => {
    // Simple user agent parsing
    if (userAgent.includes('Chrome')) return 'Chrome';
    if (userAgent.includes('Firefox')) return 'Firefox';
    if (userAgent.includes('Safari')) return 'Safari';
    if (userAgent.includes('Edge')) return 'Edge';
    return 'Unknown Browser';
  };

  const getDeviceInfo = (userAgent: string) => {
    if (userAgent.includes('Mobile')) return 'Mobile';
    if (userAgent.includes('Tablet')) return 'Tablet';
    return 'Desktop';
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center p-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        <span className="ml-2">Loading sessions...</span>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="bg-white rounded-lg shadow-md">
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">Active Sessions</h2>
          <p className="text-sm text-gray-600 mt-1">
            Manage your active login sessions across different devices and browsers
          </p>
        </div>

        {error && (
          <div className="mx-6 mt-4 p-4 bg-red-50 border border-red-200 rounded-md">
            <div className="flex">
              <div className="flex-shrink-0">
                <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                </svg>
              </div>
              <div className="ml-3">
                <p className="text-sm text-red-800">{error}</p>
              </div>
            </div>
          </div>
        )}

        <div className="p-6">
          {sessions.length === 0 ? (
            <div className="text-center py-8">
              <svg className="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              <h3 className="mt-2 text-sm font-medium text-gray-900">No active sessions</h3>
              <p className="mt-1 text-sm text-gray-500">You don't have any active sessions.</p>
            </div>
          ) : (
            <>
              <div className="mb-4 flex justify-between items-center">
                <p className="text-sm text-gray-600">
                  You have {sessions.length} active session{sessions.length !== 1 ? 's' : ''}
                </p>
                {sessions.length > 1 && (
                  <button
                    onClick={revokeAllOtherSessions}
                    className="px-4 py-2 text-sm font-medium text-red-600 bg-red-50 border border-red-200 rounded-md hover:bg-red-100 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
                  >
                    Revoke All Other Sessions
                  </button>
                )}
              </div>

              <div className="space-y-4">
                {sessions.map((session) => (
                  <div
                    key={session.id}
                    className={`border rounded-lg p-4 ${
                      session.isCurrent 
                        ? 'border-green-200 bg-green-50' 
                        : 'border-gray-200 bg-white'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-2">
                          <div className="flex items-center space-x-1">
                            <svg className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                            </svg>
                            <span className="text-sm font-medium text-gray-900">
                              {getBrowserInfo(session.userAgent)} on {getDeviceInfo(session.userAgent)}
                            </span>
                          </div>
                          {session.isCurrent && (
                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                              Current Session
                            </span>
                          )}
                        </div>
                        
                        <div className="mt-2 space-y-1 text-sm text-gray-600">
                          <div className="flex items-center space-x-4">
                            <span>IP: {session.ipAddress}</span>
                            <span>Started: {formatDate(session.issuedAt)}</span>
                            <span>Expires: {formatDate(session.expiresAt)}</span>
                          </div>
                        </div>
                      </div>

                      {!session.isCurrent && (
                        <button
                          onClick={() => revokeSession(session.id)}
                          disabled={revoking.has(session.id)}
                          className="ml-4 px-3 py-1 text-sm font-medium text-red-600 bg-white border border-red-300 rounded-md hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {revoking.has(session.id) ? 'Revoking...' : 'Revoke'}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 rounded-b-lg">
          <div className="flex items-center justify-between">
            <button
              onClick={loadSessions}
              className="text-sm text-blue-600 hover:text-blue-500 focus:outline-none focus:underline"
            >
              Refresh Sessions
            </button>
            <p className="text-xs text-gray-500">
              Sessions are automatically cleaned up when they expire
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
