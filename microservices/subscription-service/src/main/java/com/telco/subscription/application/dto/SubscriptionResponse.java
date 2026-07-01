package com.telco.subscription.application.dto;

import com.telco.subscription.domain.Subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * Read DTO for a subscription (status, MSISDN, tariff). Domain entities are never exposed directly
 * (ADR-015, FR-15).
 */
public record SubscriptionResponse(
        UUID id,
        UUID customerId,
        String msisdn,
        String tariffCode,
        int tariffVersion,
        String status,
        Instant activatedAt,
        Instant terminatedAt,
        Instant createdAt
) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getCustomerId(),
                subscription.getMsisdn(),
                subscription.getTariffCode(),
                subscription.getTariffVersion(),
                subscription.getStatus().name(),
                subscription.getActivatedAt(),
                subscription.getTerminatedAt(),
                subscription.getCreatedAt()
        );
    }
}
