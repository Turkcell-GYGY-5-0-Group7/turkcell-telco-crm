package com.telco.order.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for {@code payment.refunded.v1} consumed from the {@code payment.events} Kafka
 * topic (saga compensation). Mirrors the payment-service outbox payload ({@code PaymentRefundedEvent});
 * unknown fields are ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundedPayload(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String reason,
        String occurredAt
) {
}
