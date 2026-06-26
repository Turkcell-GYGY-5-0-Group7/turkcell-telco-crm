package com.telco.catalog.domain.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;

/**
 * Versioned event payload published to the outbox as {@code tariff.price-changed.v1} (ADR-009, ADR-019).
 * Carries both old and new price so consumers can react without fetching the full tariff.
 */
public record TariffPriceChangedEvent(
        String tariffId,
        String code,
        BigDecimal oldMonthlyFee,
        BigDecimal newMonthlyFee,
        String currency,
        int newVersion,
        String changedAt
) implements Event {
}
