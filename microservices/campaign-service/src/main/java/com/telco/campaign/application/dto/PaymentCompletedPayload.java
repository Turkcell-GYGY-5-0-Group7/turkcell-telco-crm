package com.telco.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for {@code payment.completed.v1} consumed from the {@code payment.events}
 * Kafka topic (Feature 21.4.2). Mirrors {@code payment-completed.avsc} /
 * order-service's own local copy ({@code PaymentCompletedPayload}); campaign-service cannot import
 * payment-service's or order-service's internal DTOs across the service boundary (ADR-006), so this
 * is a local, minimal copy carrying only the fields this consumer needs. Unknown fields are ignored
 * for forward-compatibility (ADR-019).
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
