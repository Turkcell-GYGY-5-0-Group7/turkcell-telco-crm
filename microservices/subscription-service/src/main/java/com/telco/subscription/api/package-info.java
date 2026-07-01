/**
 * Thin REST controllers (HTTP -> Mediator -> ApiResult) for subscription-service. Controllers carry
 * no business logic (ADR-008); they translate requests into commands/queries and dispatch via the
 * Mediator. External APIs live under {@code /api/v1} and return {@code ApiResult<T>} (ADR-015).
 *
 * <p>Endpoints and handlers are delivered by Feature 9.2 / 9.3 (domain-engineer); this scaffold ships
 * only the security configuration.
 */
package com.telco.subscription.api;
