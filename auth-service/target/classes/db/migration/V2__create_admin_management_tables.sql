-- V2: Admin Management Tables
-- Creates tables for admin creation sessions and audit logging

-- Admin Creation Sessions Table
-- Manages multi-step admin creation process
CREATE TABLE admin_creation_sessions (
    session_id UUID PRIMARY KEY,
    current_admin_id UUID NOT NULL,              -- Admin creating new admin
    new_admin_id UUID,                           -- New admin being created (set in step 2)
    admin_details JSONB NOT NULL,                -- Stored admin details from step 1
    mfa_secret VARCHAR(1024),                    -- Temporary MFA secret (encrypted)
    step INTEGER NOT NULL DEFAULT 1,             -- Current step (1, 2, 3)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '1 hour',
    
    -- Indexes for performance
    CONSTRAINT valid_step CHECK (step IN (1, 2, 3))
);

-- Index for cleanup of expired sessions
CREATE INDEX idx_admin_creation_sessions_expires_at ON admin_creation_sessions(expires_at);

-- Admin Audit Log Table
-- Tracks all admin operations for security and compliance
CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL,                      -- Admin performing action
    target_admin_id UUID,                        -- Admin being acted upon (nullable for login/logout)
    action VARCHAR(50) NOT NULL,                 -- CREATE, UPDATE, DELETE, LOGIN, LOGOUT, MFA_VERIFY
    details JSONB,                               -- Action-specific details
    ip_address INET,                             -- Source IP address
    user_agent TEXT,                             -- Browser/client information
    success BOOLEAN NOT NULL DEFAULT TRUE,       -- Whether action succeeded
    error_message TEXT,                          -- Error details if success = false
    session_id UUID,                             -- Related session ID (for creation process)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for audit log queries
CREATE INDEX idx_admin_audit_log_admin_id ON admin_audit_log(admin_id);
CREATE INDEX idx_admin_audit_log_target_admin_id ON admin_audit_log(target_admin_id);
CREATE INDEX idx_admin_audit_log_action ON admin_audit_log(action);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at);
CREATE INDEX idx_admin_audit_log_session_id ON admin_audit_log(session_id);

-- Admin Profile Cache Table (Optional)
-- Caches admin user information from Keycloak for faster queries
CREATE TABLE admin_profiles_cache (
    user_id UUID PRIMARY KEY,                    -- Keycloak User ID
    username VARCHAR(255) NOT NULL UNIQUE,       -- Username from Keycloak
    email VARCHAR(255) NOT NULL,                 -- Email from Keycloak
    first_name VARCHAR(255),                     -- First name from Keycloak
    last_name VARCHAR(255),                      -- Last name from Keycloak
    enabled BOOLEAN NOT NULL DEFAULT TRUE,       -- Account status
    last_login TIMESTAMPTZ,                      -- Last successful login
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,  -- MFA status (synced from admin_mfa_config)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for username lookups
CREATE INDEX idx_admin_profiles_cache_username ON admin_profiles_cache(username);
CREATE INDEX idx_admin_profiles_cache_email ON admin_profiles_cache(email);

-- Bootstrap Status Table
-- Tracks whether the first admin has been created
CREATE TABLE bootstrap_status (
    id INTEGER PRIMARY KEY DEFAULT 1,            -- Single row table
    first_admin_created BOOLEAN NOT NULL DEFAULT FALSE,
    first_admin_id UUID,                         -- ID of the first admin created
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Ensure only one row
    CONSTRAINT single_row CHECK (id = 1)
);

-- Insert initial bootstrap status
INSERT INTO bootstrap_status (first_admin_created) VALUES (FALSE);

-- Function to automatically update admin_profiles_cache.updated_at
CREATE OR REPLACE FUNCTION update_admin_profiles_cache_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for auto-updating updated_at
CREATE TRIGGER trigger_update_admin_profiles_cache_updated_at
    BEFORE UPDATE ON admin_profiles_cache
    FOR EACH ROW
    EXECUTE FUNCTION update_admin_profiles_cache_updated_at();

-- Function to clean up expired admin creation sessions
CREATE OR REPLACE FUNCTION cleanup_expired_admin_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM admin_creation_sessions 
    WHERE expires_at < NOW();
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    -- Log cleanup operation
    INSERT INTO admin_audit_log (admin_id, action, details, success)
    VALUES (
        '00000000-0000-0000-0000-000000000000'::UUID, 
        'SESSION_CLEANUP', 
        jsonb_build_object('deleted_sessions', deleted_count),
        TRUE
    );
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Comments for documentation
COMMENT ON TABLE admin_creation_sessions IS 'Manages multi-step admin creation process with temporary session data';
COMMENT ON TABLE admin_audit_log IS 'Comprehensive audit log for all admin operations and security events';
COMMENT ON TABLE admin_profiles_cache IS 'Cached admin profile data from Keycloak for performance';
COMMENT ON TABLE bootstrap_status IS 'Tracks system bootstrap status to prevent multiple first admin creation';

COMMENT ON COLUMN admin_creation_sessions.session_id IS 'Unique session identifier for multi-step process';
COMMENT ON COLUMN admin_creation_sessions.current_admin_id IS 'ID of admin user creating the new admin';
COMMENT ON COLUMN admin_creation_sessions.new_admin_id IS 'ID of new admin being created (set after Keycloak user creation)';
COMMENT ON COLUMN admin_creation_sessions.admin_details IS 'JSON containing new admin details from step 1';
COMMENT ON COLUMN admin_creation_sessions.mfa_secret IS 'Encrypted TOTP secret for new admin (temporary storage)';
COMMENT ON COLUMN admin_creation_sessions.step IS 'Current step in creation process (1=details, 2=mfa_setup, 3=finalize)';

COMMENT ON COLUMN admin_audit_log.admin_id IS 'ID of admin performing the action';
COMMENT ON COLUMN admin_audit_log.target_admin_id IS 'ID of admin being acted upon (null for login/logout)';
COMMENT ON COLUMN admin_audit_log.action IS 'Type of action performed (CREATE, UPDATE, DELETE, LOGIN, etc.)';
COMMENT ON COLUMN admin_audit_log.details IS 'JSON containing action-specific details and metadata';
COMMENT ON COLUMN admin_audit_log.session_id IS 'Related admin creation session ID (if applicable)';
