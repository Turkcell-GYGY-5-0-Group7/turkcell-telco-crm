package com.telco.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * JSON payload shape for {@code order.created.v1} consumed from order-service's {@code order.events}
 * Kafka topic (Feature 21.4.3). Mirrors {@code order-created.avsc} /
 * order-service's own {@code OrderCreatedEvent}; campaign-service cannot import order-service's
 * internal DTOs across the service boundary (ADR-006), so this is a local, minimal copy carrying only
 * the fields this consumer needs (per-item {@code campaignId}, added Feature 21.3.3). Unknown fields
 * are ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedPayload(
        String orderId,
        String customerId,
        List<OrderItemPayload> items
) {

    /** Nested line-item snapshot; only {@code campaignId} matters to this consumer. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItemPayload(String campaignId) {
    }
}
