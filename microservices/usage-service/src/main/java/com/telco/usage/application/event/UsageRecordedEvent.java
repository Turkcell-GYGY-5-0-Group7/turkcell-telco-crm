package com.telco.usage.application.event;

/** Outbox payload for {@code usage.recorded.v1} (ADR-009, ADR-019). */
public record UsageRecordedEvent(
        String usageRecordId,
        String subscriptionId,
        String type,
        long quantity,
        boolean overage,
        String recordedAt
) {
}
