GRANT SELECT, INSERT ON TABLE investment, withdrawal TO investment_app;
-- PostgreSQL requires UPDATE on at least one column for SELECT ... FOR UPDATE.
-- The API uses this only to lock investment rows; its SQL never updates created_at.
GRANT UPDATE (created_at) ON TABLE investment TO investment_app;

-- Bound lock waits and individual SQL statements for new API connections.
ALTER ROLE investment_app IN DATABASE investment_manager SET lock_timeout = '3s';
ALTER ROLE investment_app IN DATABASE investment_manager SET statement_timeout = '10s';
