package com.telco.subscription.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for the {@code payment.completed.v1} event consumed from Kafka. Fields mirror the
 * payment-service outbox payload. Unknown fields are ignored for forward-compatibility (ADR-019).
 *
 * <p>{@code orderId} is the saga correlation key used to fetch the order (the single synchronous hop)
 * and to key the compensation event on failure. {@code customerId} is present on the payload but the
 * activation path uses the order-service's customerId as authoritative.
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
