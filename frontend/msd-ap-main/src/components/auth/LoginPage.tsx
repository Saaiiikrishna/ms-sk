import React, { useState } from 'react';
import { Shield, Eye, EyeOff, Smartphone, AlertCircle } from 'lucide-react';
import { authService } from '../../services';

interface LoginPageProps {
  onLogin: () => void;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onLogin }) => {
  console.log('LoginPage component rendered');
  const [email, setEmail] = useState('admin@mysillydreams.com');
  const [password, setPassword] = useState('admin123');
  const [otpCode, setOtpCode] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showMFA, setShowMFA] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Test API connection directly
  const testApiConnection = async () => {
    console.log('Testing API connection...');
    try {
      const response = await fetch('/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username: email,
          password: password
        })
      });
      console.log('API Response:', response);
      const data = await response.text();
      console.log('API Data:', data);
    } catch (error) {
      console.error('API Error:', error);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    console.log('Login form submitted', { email, password: '***' });
    setIsLoading(true);
    setError(null);

    try {
      if (!showMFA) {
        // Initial login with email/password
        console.log('Making login request...');
        const response = await authService.login({
          username: email,
          password: password
        });
        console.log('Login response:', response);

        if (response.success && response.data) {
          // Check if user has MFA enabled
          if (response.data.user.mfaEnabled) {
            setShowMFA(true);
          } else {
            // Direct login success (no MFA required)
            onLogin();
          }
        } else {
          setError(response.error || 'Login failed. Please check your credentials.');
        }
      } else {
        // MFA verification
        const response = await authService.login({
          username: email,
          password: password,
          otpCode: otpCode
        });

        if (response.success) {
          onLogin();
        } else {
          setError(response.error || 'Invalid MFA code. Please try again.');
        }
      }
    } catch (error) {
      setError('Network error. Please try again.');
      console.error('Login error:', error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-8">
        <div className="text-center mb-8">
          <div className="bg-blue-600 rounded-full w-16 h-16 flex items-center justify-center mx-auto mb-4">
            <Shield className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">MySillyDreams</h1>
          <p className="text-gray-600">Admin Panel</p>
        </div>

        <form onSubmit={(e) => { console.log('Form submitted!'); handleSubmit(e); }} className="space-y-6">
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center space-x-3">
              <AlertCircle className="w-5 h-5 text-red-500 flex-shrink-0" />
              <p className="text-red-700 text-sm">{error}</p>
            </div>
          )}

          {!showMFA ? (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Email Address
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors"
                  placeholder="admin@mysillydeams.com"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Password
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors pr-12"
                    placeholder="Enter your password"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 hover:text-gray-700"
                  >
                    {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                <Smartphone className="inline w-4 h-4 mr-2" />
                Two-Factor Authentication
              </label>
              <input
                type="text"
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-center tracking-widest"
                placeholder="000000"
                maxLength={6}
                required
              />
              <p className="text-sm text-gray-600 mt-2">
                Enter the 6-digit code from your authenticator app
              </p>
            </div>
          )}

          <button
            type="submit"
            disabled={isLoading}
            onClick={() => console.log('Button clicked!')}
            className="w-full bg-blue-600 text-white py-3 px-4 rounded-lg font-medium hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? 'Signing In...' : showMFA ? 'Verify & Sign In' : 'Sign In'}
          </button>

          <button
            type="button"
            onClick={testApiConnection}
            className="w-full bg-green-600 text-white py-2 px-4 rounded-lg font-medium hover:bg-green-700 text-sm"
          >
            Test API Connection
          </button>

          <div className="mt-4 p-4 bg-gray-100 rounded-lg">
            <p className="text-sm text-gray-600 mb-2">Direct API Test:</p>
            <button
              onClick={() => {
                console.log('Direct test button clicked!');
                fetch('/auth/login', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ username: 'admin@mysillydreams.com', password: 'admin123' })
                })
                .then(response => {
                  console.log('Response status:', response.status);
                  return response.text();
                })
                .then(data => console.log('Response data:', data))
                .catch(error => console.error('Error:', error));
              }}
              className="bg-blue-500 text-white px-4 py-2 rounded text-sm"
            >
              Direct API Test
            </button>
            <button
              onClick={() => {
                console.log('Testing auth service directly...');
                fetch('http://localhost:8081/login', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ username: 'admin@mysillydreams.com', password: 'admin123' })
                })
                .then(response => {
                  console.log('Direct auth service response status:', response.status);
                  return response.text();
                })
                .then(data => console.log('Direct auth service response data:', data))
                .catch(error => console.error('Direct auth service error:', error));
              }}
              className="bg-purple-500 text-white px-4 py-2 rounded text-sm ml-2"
            >
              Test Auth Service Direct
            </button>
          </div>

          {showMFA && (
            <button
              type="button"
              onClick={() => setShowMFA(false)}
              className="w-full text-gray-600 hover:text-gray-800 text-sm font-medium"
            >
              ← Back to login
            </button>
          )}
        </form>
      </div>
    </div>
  );
};