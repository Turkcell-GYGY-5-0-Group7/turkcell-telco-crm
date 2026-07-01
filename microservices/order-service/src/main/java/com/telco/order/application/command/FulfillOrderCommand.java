package com.telco.order.application.command;

import com.telco.order.application.dto.OrderResponse;
import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Fulfills a CONFIRMED order after its subscription is activated (saga step
 * {@code SUBSCRIPTION_ACTIVATED}). Dispatched by the {@code subscription.activated.v1} consumer.
 * Drives {@code Order.fulfill()} (CONFIRMED -&gt; FULFILLED, terminal success) and advances
 * saga_state. No order domain event is emitted: the transition is a local state change (AC-01).
 *
 * <p>Saga-only command: implements {@link IdempotentRequest} with {@link #idempotencyKey()} carrying
 * the Kafka {@code messageId} (the record key) so the platform {@code InboxBehavior} dedups
 * redelivery atomically INSIDE the handler transaction (tech-lead ruling 2a/2b).
 */
public record FulfillOrderCommand(

        @NotNull
        UUID orderId,

        /** subscription id for saga payload / traceability (may be null). */
        String subscriptionId,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotNull
        String messageId

) implements Command<OrderResponse>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
