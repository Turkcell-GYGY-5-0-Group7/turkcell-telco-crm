package com.telco.usage.application.event;

/**
 * Outbox payload for {@code quota.threshold-reached.v1} (ADR-009, ADR-019).
 *
 * <p>{@code customerId} is nullable and backward-compatible: populated from the owning
 * {@link com.telco.usage.domain.Quota}'s stored {@code customerId} so notification-service can
 * route the 80% warning SMS to the real customer instead of falling back to {@code unknown}. Null
 * only for quotas provisioned before this field existed.
 */
public record QuotaThresholdReachedEvent(
        String subscriptionId,
        String quotaId,
        String usageType,
        String customerId,
        String reachedAt
) {
}
