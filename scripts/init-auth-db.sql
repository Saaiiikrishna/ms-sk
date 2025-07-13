-- Auth Service Database Initialization Script

-- Create database if not exists (PostgreSQL doesn't support IF NOT EXISTS for databases in this context)
-- The database is created by the POSTGRES_DB environment variable

-- Connect to the authdb database
\c authdb;

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create admin_profiles table
CREATE TABLE IF NOT EXISTS admin_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    role VARCHAR(20) DEFAULT 'ADMIN',
    is_active BOOLEAN DEFAULT true,
    is_mfa_enabled BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    password_changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create admin_audit_log table
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id UUID REFERENCES admin_profiles(id),
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(100),
    details TEXT,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create password_rotation_log table
CREATE TABLE IF NOT EXISTS password_rotation_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id UUID REFERENCES admin_profiles(id),
    old_password_hash VARCHAR(255),
    new_password_hash VARCHAR(255),
    rotation_reason VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create admin_creation_session table
CREATE TABLE IF NOT EXISTS admin_creation_session (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_token VARCHAR(255) UNIQUE NOT NULL,
    creator_admin_id UUID REFERENCES admin_profiles(id),
    target_username VARCHAR(50) NOT NULL,
    target_email VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create admin_mfa_config table
CREATE TABLE IF NOT EXISTS admin_mfa_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id UUID REFERENCES admin_profiles(id) UNIQUE,
    secret_key VARCHAR(255) NOT NULL,
    backup_codes TEXT[],
    is_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_admin_profiles_username ON admin_profiles(username);
CREATE INDEX IF NOT EXISTS idx_admin_profiles_email ON admin_profiles(email);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_id ON admin_audit_log(admin_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_password_rotation_log_admin_id ON password_rotation_log(admin_id);
CREATE INDEX IF NOT EXISTS idx_admin_creation_session_token ON admin_creation_session(session_token);
CREATE INDEX IF NOT EXISTS idx_admin_mfa_config_admin_id ON admin_mfa_config(admin_id);

-- Insert default admin user (password: admin123)
INSERT INTO admin_profiles (username, email, password_hash, first_name, last_name, role)
VALUES (
    'admin',
    'admin@mysillydreams.com',
    '$2a$10$rZ8R1zKz1zKz1zKz1zKz1uO1zKz1zKz1zKz1zKz1zKz1zKz1zKz1zK',
    'System',
    'Administrator',
    'SUPER_ADMIN'
) ON CONFLICT (username) DO NOTHING;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO authuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO authuser;
