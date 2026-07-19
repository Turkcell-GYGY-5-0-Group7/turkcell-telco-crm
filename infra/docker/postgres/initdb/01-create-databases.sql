-- PostgreSQL per-service user isolation (ADR-006).
-- Runs once on first container start as POSTGRES_USER (superuser).
-- Pattern per service:
--   1. CREATE USER with explicit non-superuser attributes.
--   2. CREATE DATABASE owned by that user (PG15+ makes the user owner of pg_database_owner,
--      which owns the public schema — Flyway can run DDL without extra schema grants).
--   3. REVOKE ALL FROM PUBLIC — removes the default CONNECT grant to the PUBLIC role.
--   4. GRANT CONNECT back to the service user and to the debezium CDC user explicitly.
-- The superuser (telco/POSTGRES_USER) is never used by application services.

-- Debezium CDC connector (REPLICATION privilege only; SELECT grants applied in 02-init-schemas.sql)
CREATE USER debezium WITH REPLICATION LOGIN PASSWORD 'debezium'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

-- identity-service
CREATE USER identity WITH LOGIN PASSWORD 'identity'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE identity_db OWNER identity;
REVOKE ALL ON DATABASE identity_db FROM PUBLIC;
GRANT CONNECT ON DATABASE identity_db TO identity;
GRANT CONNECT ON DATABASE identity_db TO debezium;

-- customer-service
CREATE USER customer WITH LOGIN PASSWORD 'customer'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE customer_db OWNER customer;
REVOKE ALL ON DATABASE customer_db FROM PUBLIC;
GRANT CONNECT ON DATABASE customer_db TO customer;
GRANT CONNECT ON DATABASE customer_db TO debezium;

-- product-catalog-service
CREATE USER product_catalog WITH LOGIN PASSWORD 'product_catalog'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE product_catalog_db OWNER product_catalog;
REVOKE ALL ON DATABASE product_catalog_db FROM PUBLIC;
GRANT CONNECT ON DATABASE product_catalog_db TO product_catalog;
GRANT CONNECT ON DATABASE product_catalog_db TO debezium;

-- order-service (ORDER is a SQL reserved word; must be double-quoted in SQL)
CREATE USER "order" WITH LOGIN PASSWORD 'order'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE order_db OWNER "order";
REVOKE ALL ON DATABASE order_db FROM PUBLIC;
GRANT CONNECT ON DATABASE order_db TO "order";
GRANT CONNECT ON DATABASE order_db TO debezium;

-- subscription-service
CREATE USER subscription WITH LOGIN PASSWORD 'subscription'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE subscription_db OWNER subscription;
REVOKE ALL ON DATABASE subscription_db FROM PUBLIC;
GRANT CONNECT ON DATABASE subscription_db TO subscription;
GRANT CONNECT ON DATABASE subscription_db TO debezium;

-- usage-service
CREATE USER usage WITH LOGIN PASSWORD 'usage'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE usage_db OWNER usage;
REVOKE ALL ON DATABASE usage_db FROM PUBLIC;
GRANT CONNECT ON DATABASE usage_db TO usage;
GRANT CONNECT ON DATABASE usage_db TO debezium;

-- billing-service
CREATE USER billing WITH LOGIN PASSWORD 'billing'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE billing_db OWNER billing;
REVOKE ALL ON DATABASE billing_db FROM PUBLIC;
GRANT CONNECT ON DATABASE billing_db TO billing;
GRANT CONNECT ON DATABASE billing_db TO debezium;

-- payment-service
CREATE USER payment WITH LOGIN PASSWORD 'payment'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE payment_db OWNER payment;
REVOKE ALL ON DATABASE payment_db FROM PUBLIC;
GRANT CONNECT ON DATABASE payment_db TO payment;
GRANT CONNECT ON DATABASE payment_db TO debezium;

-- notification-service (PostgreSQL store for outbox/inbox only; MongoDB is separate per ADR-006)
CREATE USER notification WITH LOGIN PASSWORD 'notification'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE notification_db OWNER notification;
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;
GRANT CONNECT ON DATABASE notification_db TO notification;
GRANT CONNECT ON DATABASE notification_db TO debezium;

-- ticket-service
CREATE USER ticket WITH LOGIN PASSWORD 'ticket'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE ticket_db OWNER ticket;
REVOKE ALL ON DATABASE ticket_db FROM PUBLIC;
GRANT CONNECT ON DATABASE ticket_db TO ticket;
GRANT CONNECT ON DATABASE ticket_db TO debezium;

-- campaign-service
CREATE USER campaign WITH LOGIN PASSWORD 'campaign'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE campaign_db OWNER campaign;
REVOKE ALL ON DATABASE campaign_db FROM PUBLIC;
GRANT CONNECT ON DATABASE campaign_db TO campaign;
GRANT CONNECT ON DATABASE campaign_db TO debezium;

-- reference-service (demo; no domain events; debezium not granted)
CREATE USER reference WITH LOGIN PASSWORD 'reference'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE reference_db OWNER reference;
REVOKE ALL ON DATABASE reference_db FROM PUBLIC;
GRANT CONNECT ON DATABASE reference_db TO reference;

-- Keycloak — third-party component; no _db suffix by exception (approved in tech-lead ruling).
-- Changing the name breaks Keycloak's out-of-box realm import; no security benefit.
CREATE USER keycloak WITH LOGIN PASSWORD 'keycloak'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
GRANT CONNECT ON DATABASE keycloak TO keycloak;
