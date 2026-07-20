package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.math.BigDecimal;
import java.time.Instant;

/** Refreshes billing's local tariff price mirror from {@code tariff.price-changed.v1} (FR-08). */
public record RecordTariffPriceChangedCommand(
        String tariffCode,
        BigDecimal newMonthlyFee,
        String currency,
        Instant changedAt
) implements Command<Void> {}
