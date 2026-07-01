package com.telco.usage.application.dto;

import com.telco.usage.domain.Quota;

import java.time.Instant;
import java.util.UUID;

/** Read-model projection of a quota aggregate for external consumers. */
public record QuotaResponse(
        UUID quotaId,
        UUID subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        long minutesTotal,
        long smsTotal,
        long mbTotal,
        long minutesRemaining,
        long smsRemaining,
        long mbRemaining
) {

    public static QuotaResponse from(Quota quota) {
        return new QuotaResponse(
                quota.getId(),
                quota.getSubscriptionId(),
                quota.getPeriodStart(),
                quota.getPeriodEnd(),
                quota.getMinutesTotal(),
                quota.getSmsTotal(),
                quota.getMbTotal(),
                quota.getMinutesRemaining(),
                quota.getSmsRemaining(),
                quota.getMbRemaining());
    }
}
