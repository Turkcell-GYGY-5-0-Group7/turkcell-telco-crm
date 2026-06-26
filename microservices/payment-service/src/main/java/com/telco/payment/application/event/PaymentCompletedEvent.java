package com.telco.payment.application.event;

import java.math.BigDecimal;

/**
 * Outbox payload for {@code payment.completed.v1}.
 * Serialized to JSON by {@link com.telco.platform.outbox.OutboxService} (ADR-009, ADR-019).
 */
public record PaymentCompletedEvent(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String occurredAt
) {
}
