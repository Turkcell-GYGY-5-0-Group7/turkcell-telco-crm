package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Records an addon fee for the next invoice (FR-22), from subscription.addon-attached.v1. */
public record RecordAddonChargeCommand(
        UUID subscriptionId,
        UUID orderId,
        String addonCode,
        String addonType,
        BigDecimal price,
        String currency,
        Instant attachedAt
) implements Command<Void> {}
