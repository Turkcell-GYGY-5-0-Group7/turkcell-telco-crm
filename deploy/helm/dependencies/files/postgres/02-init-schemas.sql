-- Schema-level grants for Debezium CDC read access (ADR-009, ADR-019).
-- Grants SELECT on all FUTURE tables created by each service user in the public schema
-- via ALTER DEFAULT PRIVILEGES. Runs as POSTGRES_USER (superuser) via \c metacommand,
-- which Docker's PostgreSQL entrypoint supports in initdb .sql files.
--
-- Service users own their databases (and therefore the public schema in PG15+),
-- so no extra schema grants are needed for Flyway to run DDL.
-- reference_db is excluded — reference-service publishes no domain events.

\c identity_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE identity IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE identity IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c customer_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE customer IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE customer IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c product_catalog_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE product_catalog IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE product_catalog IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c order_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE "order" IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE "order" IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c subscription_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE subscription IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE subscription IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c usage_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE usage IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE usage IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c billing_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE billing IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE billing IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c payment_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE payment IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE payment IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c notification_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE notification IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE notification IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;

\c ticket_db
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE ticket IN SCHEMA public GRANT SELECT ON TABLES TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE ticket IN SCHEMA public GRANT SELECT ON SEQUENCES TO debezium;
