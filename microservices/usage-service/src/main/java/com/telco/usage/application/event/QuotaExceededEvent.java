package com.telco.usage.application.event;

/**
 * Outbox payload for {@code quota.exceeded.v1} (ADR-009, ADR-019).
 *
 * <p>{@code customerId} is nullable and backward-compatible: populated from the owning
 * {@link com.telco.usage.domain.Quota}'s stored {@code customerId} so notification-service can
 * route the exhaustion SMS to the real customer instead of falling back to {@code unknown}. Null
 * only for quotas provisioned before this field existed.
 */
public record QuotaExceededEvent(
        String subscriptionId,
        String quotaId,
        String usageType,
        String customerId,
        String exceededAt
) {
}
