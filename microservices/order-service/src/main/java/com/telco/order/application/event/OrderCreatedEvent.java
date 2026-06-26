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

    /** Lightweight item snapshot embedded in the event. */
    public record OrderItemPayload(
            String tariffId,
            String tariffName,
            BigDecimal unitPrice,
            int quantity
    ) {
    }
}
