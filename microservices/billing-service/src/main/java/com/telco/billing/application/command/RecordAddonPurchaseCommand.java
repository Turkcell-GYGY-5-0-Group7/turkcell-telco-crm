package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records one addon purchase as an unbilled {@code addon_charge_record} row (Sprint 24 Feature
 * 24.3, design-note D3, FR-22). Dispatched by the {@code addon.purchased.v1} consumer; {@code price}
 * arrives as the FULL purchase amount (event unit price already multiplied by quantity).
 *
 * <p>Saga-only command: implements {@link IdempotentRequest} with {@link #idempotencyKey()}
 * carrying the Kafka {@code messageId} (the record key = the producer's outbox aggregate_id, the
 * order-item id), so the platform {@code InboxBehavior} dedups redelivery atomically INSIDE the
 * handler transaction - a redelivered purchase can never produce a second charge row.
 */
public record RecordAddonPurchaseCommand(

        @NotNull
        UUID subscriptionId,

        @NotNull
        UUID customerId,

        @NotNull
        String addonCode,

        String addonName,

        @NotNull
        BigDecimal price,

        @NotNull
        String currency,

        @NotNull
        Instant purchasedAt,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotNull
        String messageId

) implements Command<Void>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
