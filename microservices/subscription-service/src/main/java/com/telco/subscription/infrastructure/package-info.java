/**
 * Infrastructure layer for subscription-service: Spring Data repositories and outbound adapters.
 *
 * <p>Domain repositories (Subscription, MSISDN pool, SIM card) and inbox consumers for the onboarding
 * saga are delivered by Feature 9.2 / 9.3 (domain-engineer); this scaffold ships only the audit-log
 * repository ({@link com.telco.subscription.infrastructure.AuditLogRepository}).
 */
package com.telco.subscription.infrastructure;
