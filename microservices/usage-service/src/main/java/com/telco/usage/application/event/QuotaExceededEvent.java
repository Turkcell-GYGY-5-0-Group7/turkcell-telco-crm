package com.telco.usage.application.event;

/** Outbox payload for {@code quota.exceeded.v1} (ADR-009, ADR-019). */
public record QuotaExceededEvent(
        String subscriptionId,
        String quotaId,
        String usageType,
        String exceededAt
) {
}
