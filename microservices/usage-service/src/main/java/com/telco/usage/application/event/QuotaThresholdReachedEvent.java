package com.telco.usage.application.event;

/** Outbox payload for {@code quota.threshold-reached.v1} (ADR-009, ADR-019). */
public record QuotaThresholdReachedEvent(
        String subscriptionId,
        String quotaId,
        String usageType,
        String reachedAt
) {
}
