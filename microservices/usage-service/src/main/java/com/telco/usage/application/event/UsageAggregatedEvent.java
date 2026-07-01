package com.telco.usage.application.event;

/** Outbox payload for {@code usage.aggregated.v1} (ADR-009, ADR-019). */
public record UsageAggregatedEvent(
        String subscriptionId,
        String periodStart,
        String periodEnd,
        long voiceOverageSeconds,
        long smsOverageCount,
        long dataOverageKb,
        String aggregatedAt
) {
}
