-- Create refresh_tokens table for secure token storage
-- This provides better security than storing refresh tokens as JWTs

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(45), -- Support IPv6 addresses
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_username ON refresh_tokens(username);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_revoked ON refresh_tokens(revoked);
CREATE INDEX idx_refresh_tokens_ip_address ON refresh_tokens(ip_address);

-- Create composite indexes for common queries
CREATE INDEX idx_refresh_tokens_username_valid ON refresh_tokens(username, revoked, expires_at);
CREATE INDEX idx_refresh_tokens_user_id_valid ON refresh_tokens(user_id, revoked, expires_at);

-- Add comments for documentation
COMMENT ON TABLE refresh_tokens IS 'Stores refresh tokens securely with metadata for session management';
COMMENT ON COLUMN refresh_tokens.token IS 'Cryptographically secure random token string';
COMMENT ON COLUMN refresh_tokens.username IS 'Username associated with the token';
COMMENT ON COLUMN refresh_tokens.user_id IS 'User ID associated with the token';
COMMENT ON COLUMN refresh_tokens.issued_at IS 'When the token was issued';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'When the token expires';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Whether the token has been revoked';
COMMENT ON COLUMN refresh_tokens.ip_address IS 'IP address where token was issued (for security tracking)';
COMMENT ON COLUMN refresh_tokens.user_agent IS 'User agent where token was issued (for security tracking)';
