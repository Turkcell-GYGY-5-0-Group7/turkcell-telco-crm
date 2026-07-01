package com.telco.order.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for {@code payment.completed.v1} consumed from the {@code payment.events}
 * Kafka topic. Mirrors the payment-service outbox payload ({@code PaymentCompletedEvent}); unknown
 * fields are ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedPayload(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String occurredAt
) {
}
