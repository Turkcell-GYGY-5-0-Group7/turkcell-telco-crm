package com.telco.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for the {@code order.created.v1} event consumed from Kafka.
 * Fields match the order-service outbox event payload. Unknown fields are ignored
 * for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedPayload(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String occurredAt
) {
}
