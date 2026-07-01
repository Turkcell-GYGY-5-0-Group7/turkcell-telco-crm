package com.telco.usage.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Billing-relevant overage totals for a subscription's billing period. */
public record UsageAggregateResponse(
        UUID subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        long voiceOverageSeconds,
        long smsOverageCount,
        long dataOverageKb
) {
}
