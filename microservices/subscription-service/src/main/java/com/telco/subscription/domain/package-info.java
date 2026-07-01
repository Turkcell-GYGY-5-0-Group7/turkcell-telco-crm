/**
 * Domain layer for subscription-service: aggregates and value objects for the Subscription, MSISDN
 * pool and SIM-card bounded context (FR-13, FR-15). JPA entities live here for brevity. The domain is
 * framework-independent of platform-core (ADR-018).
 *
 * <p>The subscription state machine (ACTIVE/SUSPENDED/TERMINATED) and MSISDN allocation rules
 * (FREE/RESERVED/ALLOCATED) are delivered by Feature 9.2 / 9.3 (domain-engineer); this scaffold ships
 * only the audit-log aggregate ({@link com.telco.subscription.domain.AuditLog}).
 */
package com.telco.subscription.domain;
