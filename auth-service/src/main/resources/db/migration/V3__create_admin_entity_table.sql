-- Migration V3: Create Admin Entity Table
-- Creates the primary admin table for storing admin data in Auth Service database
-- Hybrid approach: Admin data stored internally, Keycloak used for authentication APIs

-- Admin Table
-- Primary table for storing admin users in Auth Service database
CREATE TABLE IF NOT EXISTS admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    keycloak_user_id VARCHAR(255), -- Link to Keycloak user for authentication
    last_login TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    
    -- Foreign key constraints
    CONSTRAINT fk_admins_created_by FOREIGN KEY (created_by) REFERENCES admins(id),
    CONSTRAINT fk_admins_updated_by FOREIGN KEY (updated_by) REFERENCES admins(id),
    
    -- Check constraints
    CONSTRAINT chk_admins_username_length CHECK (LENGTH(username) >= 3),
    CONSTRAINT chk_admins_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_admins_failed_attempts CHECK (failed_login_attempts >= 0),
    CONSTRAINT chk_admins_names_not_empty CHECK (LENGTH(TRIM(first_name)) > 0 AND LENGTH(TRIM(last_name)) > 0)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_admins_username ON admins(username);
CREATE INDEX IF NOT EXISTS idx_admins_email ON admins(email);
CREATE INDEX IF NOT EXISTS idx_admins_keycloak_user_id ON admins(keycloak_user_id);
CREATE INDEX IF NOT EXISTS idx_admins_enabled ON admins(enabled);
CREATE INDEX IF NOT EXISTS idx_admins_mfa_enabled ON admins(mfa_enabled);
CREATE INDEX IF NOT EXISTS idx_admins_last_login ON admins(last_login);
CREATE INDEX IF NOT EXISTS idx_admins_created_at ON admins(created_at);
CREATE INDEX IF NOT EXISTS idx_admins_locked_until ON admins(locked_until);
CREATE INDEX IF NOT EXISTS idx_admins_failed_attempts ON admins(failed_login_attempts);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_admins_enabled_username ON admins(enabled, username);
CREATE INDEX IF NOT EXISTS idx_admins_mfa_enabled_username ON admins(mfa_enabled, username);

-- Function to automatically update admins.updated_at
CREATE OR REPLACE FUNCTION update_admins_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for auto-updating updated_at
CREATE TRIGGER trigger_update_admins_updated_at
    BEFORE UPDATE ON admins
    FOR EACH ROW
    EXECUTE FUNCTION update_admins_updated_at();

-- Comments for documentation
COMMENT ON TABLE admins IS 'Primary admin users table storing admin data in Auth Service database (hybrid approach)';
COMMENT ON COLUMN admins.id IS 'Primary key - UUID for admin identification';
COMMENT ON COLUMN admins.username IS 'Unique username for admin login';
COMMENT ON COLUMN admins.email IS 'Unique email address for admin';
COMMENT ON COLUMN admins.password_hash IS 'Hashed password for admin authentication';
COMMENT ON COLUMN admins.enabled IS 'Whether admin account is enabled';
COMMENT ON COLUMN admins.mfa_enabled IS 'Whether MFA is enabled for this admin';
COMMENT ON COLUMN admins.keycloak_user_id IS 'Link to Keycloak user ID for authentication APIs';
COMMENT ON COLUMN admins.last_login IS 'Timestamp of last successful login';
COMMENT ON COLUMN admins.password_changed_at IS 'Timestamp when password was last changed';
COMMENT ON COLUMN admins.failed_login_attempts IS 'Number of consecutive failed login attempts';
COMMENT ON COLUMN admins.locked_until IS 'Timestamp until which account is locked (NULL if not locked)';
COMMENT ON COLUMN admins.created_by IS 'Admin who created this admin account';
COMMENT ON COLUMN admins.updated_by IS 'Admin who last updated this admin account';
