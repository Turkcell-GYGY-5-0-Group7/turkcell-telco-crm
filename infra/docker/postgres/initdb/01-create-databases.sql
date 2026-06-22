-- Database-per-service (ADR-006). One database per microservice plus one for Keycloak.
-- Runs once on first container start (empty data volume). Owned by the default POSTGRES_USER.
-- For local development only.

CREATE DATABASE identity;
CREATE DATABASE customer;
CREATE DATABASE product_catalog;
CREATE DATABASE "order";
CREATE DATABASE subscription;
CREATE DATABASE usage;
CREATE DATABASE billing;
CREATE DATABASE payment;
CREATE DATABASE notification;
CREATE DATABASE ticket;

-- Backing store for the Keycloak (auth profile) service.
CREATE DATABASE keycloak;

-- Demo database for the reference-service example.
CREATE DATABASE reference;
