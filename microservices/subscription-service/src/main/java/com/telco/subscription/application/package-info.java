/**
 * Application layer for subscription-service (CQRS + Mediator, ADR-004): commands, queries, their
 * handlers, DTOs and versioned event payloads.
 *
 * <p>Commands mutate state and publish domain events via the transactional outbox; the mediator
 * TransactionBehavior makes the DB write and the outbox row atomic (ADR-005, ADR-009). Queries never
 * change state. Handlers are delivered by Feature 9.2 / 9.3 (domain-engineer); this scaffold ships
 * only the audit-log writer ({@link com.telco.subscription.application.AuditLogWriter}).
 */
package com.telco.subscription.application;
