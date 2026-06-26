-- Adds caller identity (Keycloak subject) to orders for ownership-based access control.
-- Existing rows (if any) get 'system' as a safe sentinel; default is removed so new rows
-- must supply a value from the application.
ALTER TABLE orders ADD COLUMN user_id VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE orders ALTER COLUMN user_id DROP DEFAULT;
