package com.telco.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON payload shape for {@code order.cancelled.v1} consumed from order-service's {@code order.events}
 * Kafka topic (Feature 21.4.2). Mirrors {@code order-cancelled.avsc} /
 * order-service's own {@code OrderCancelledEvent}; campaign-service cannot import order-service's
 * internal DTOs across the service boundary (ADR-006), so this is a local, minimal copy carrying only
 * the fields this consumer needs. Unknown fields are ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledPayload(
        String orderId,
        String customerId,
        String reason,
        String occurredAt
) {
}
