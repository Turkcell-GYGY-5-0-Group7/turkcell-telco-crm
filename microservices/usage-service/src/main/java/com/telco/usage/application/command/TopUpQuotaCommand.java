package com.telco.usage.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Tops up a subscription's active quota with purchased addon allowances (Sprint 24 Feature 24.3,
 * design-note D4). Dispatched by the {@code addon.purchased.v1} consumer; the deltas arrive
 * already multiplied by the purchased quantity.
 *
 * <p>Saga-only command: implements {@link IdempotentRequest} with {@link #idempotencyKey()}
 * carrying the Kafka {@code messageId} (the record key = the order-item id the producer used as
 * outbox aggregate_id), so the platform {@code InboxBehavior} dedups redelivery atomically INSIDE
 * the handler transaction. A transiently failing handler (quota row not provisioned yet) rolls the
 * inbox row back with the transaction, so redelivery retries the top-up.
 */
public record TopUpQuotaCommand(

        @NotNull
        UUID subscriptionId,

        /** voice minutes to add (0 for none). */
        long minutes,

        /** SMS count to add (0 for none). */
        long sms,

        /** data megabytes to add (0 for none). */
        long mb,

        /** Instant the purchase was fulfilled; selects the billing period being topped up. */
        @NotNull
        Instant occurredAt,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotNull
        String messageId

) implements Command<Void>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
