package com.telco.subscription.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for the {@code payment.failed.v1} event consumed from Kafka. Fields mirror the
 * payment-service outbox payload ({@code PaymentFailedEvent}). Unknown fields are ignored for
 * forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedPayload(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String reason,
        String occurredAt
) {
}
