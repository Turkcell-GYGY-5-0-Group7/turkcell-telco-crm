package com.telco.order.application.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Versioned event payload published to the outbox as {@code order.created.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox; an {@code eventId} is injected automatically for
 * consumer idempotency. Payload matches the Avro schema contract in event-catalog.md.
 */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        String idempotencyKey,
        String occurredAt
) implements Event {

    /**
     * Lightweight item snapshot embedded in the event. {@code campaignId} is a nullable, additive
     * field (Feature 21.3.3, ADR-027 Decision Section 4 third ratification addendum, Avro-backward-
     * compatible per ADR-019): the campaign, if any, that discounted {@code unitPrice}. This is what
     * lets campaign-service's Feature 21.4 {@code order.created.v1} consumer create the correctly
     * attributed {@code RESERVED} redemption row.
     */
    public record OrderItemPayload(
            String tariffId,
            String tariffName,
            BigDecimal unitPrice,
            int quantity,
            String campaignId
    ) {
    }
}
