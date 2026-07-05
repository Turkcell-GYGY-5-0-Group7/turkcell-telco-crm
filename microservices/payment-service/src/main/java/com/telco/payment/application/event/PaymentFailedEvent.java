package com.telco.payment.application.event;

import java.math.BigDecimal;

/**
 * Outbox payload for {@code payment.failed.v1}.
 * Serialized to JSON by {@link com.telco.platform.outbox.OutboxService} (ADR-009, ADR-019).
 *
 * <p>{@code invoiceId} is nullable and backward-compatible (ADR-019): populated only when the
 * charge attempt targets a specific invoice (Section 14.2 pay-invoice flow).
 */
public record PaymentFailedEvent(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String invoiceId,
        String reason,
        String occurredAt
) {
}
