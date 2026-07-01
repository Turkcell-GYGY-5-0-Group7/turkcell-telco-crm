package com.telco.usage.application.dto;

import com.telco.usage.domain.UsageRecord;
import com.telco.usage.domain.UsageType;

import java.time.Instant;
import java.util.UUID;

/** Projection of a single usage record for the history API. */
public record UsageHistoryItem(
        UUID id,
        UUID subscriptionId,
        UsageType type,
        long quantity,
        boolean overage,
        String cdrRef,
        Instant recordedAt
) {

    public static UsageHistoryItem from(UsageRecord record) {
        return new UsageHistoryItem(
                record.getId(),
                record.getSubscriptionId(),
                record.getType(),
                record.getQuantity(),
                record.isOverage(),
                record.getCdrRef(),
                record.getRecordedAt());
    }
}
