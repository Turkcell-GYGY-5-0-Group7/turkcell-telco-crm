package com.telco.order.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code order.cancelled.v1} (ADR-009, ADR-019).
 */
public record OrderCancelledEvent(
        String orderId,
        String customerId,
        String reason,
        String occurredAt
) implements Event {
}
