package com.telco.catalog.domain.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;

/**
 * Versioned event payload published to the outbox as {@code tariff.created.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox; an {@code eventId} is injected automatically for
 * consumer idempotency.
 */
public record TariffCreatedEvent(
        String tariffId,
        String code,
        String name,
        String type,
        BigDecimal monthlyFee,
        String currency,
        String effectiveFrom,
        String createdAt
) implements Event {
}
