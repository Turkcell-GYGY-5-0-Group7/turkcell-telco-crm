package com.telco.campaign.domain.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;

/**
 * Versioned event payload published to the outbox as {@code campaign.created.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox; an {@code eventId} is injected automatically for consumer
 * idempotency. Mirrors product-catalog-service's {@code TariffCreatedEvent} shape (Feature 21.4.1).
 */
public record CampaignCreatedEvent(
        String campaignId,
        String code,
        String name,
        String discountType,
        BigDecimal discountValue,
        String validFrom,
        String validTo,
        Integer totalRedemptionCap,
        int perCustomerRedemptionCap,
        String createdAt
) implements Event {
}
