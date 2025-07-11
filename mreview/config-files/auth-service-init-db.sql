-- Database initialization script for Auth Service
-- This script sets up the initial database structure and test data

-- Create the main database (already created by Docker, but ensuring it exists)
-- CREATE DATABASE IF NOT EXISTS authdb;

-- Use the authdb database
\c authdb;

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant necessary permissions to the authuser
GRANT ALL PRIVILEGES ON DATABASE authdb TO authuser;
GRANT ALL PRIVILEGES ON SCHEMA public TO authuser;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO authuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO authuser;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO authuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO authuser;

-- The actual tables will be created by Hibernate/JPA when the application starts
-- This is just to ensure proper permissions and extensions are in place

-- Optional: Create some initial test data (uncomment if needed)
-- Note: This would typically be done after the application creates the tables

/*
-- Example of inserting test data after tables are created
-- This would need to be run after the application has started and created the tables

INSERT INTO admin_mfa_config (id, user_id, username, totp_secret_encrypted, mfa_enabled, created_at, updated_at)
VALUES 
    (uuid_generate_v4(), uuid_generate_v4(), 'test.admin', 'encrypted_secret_here', false, NOW(), NOW())
ON CONFLICT DO NOTHING;
*/

-- Log the initialization
\echo 'Database initialization completed successfully'
