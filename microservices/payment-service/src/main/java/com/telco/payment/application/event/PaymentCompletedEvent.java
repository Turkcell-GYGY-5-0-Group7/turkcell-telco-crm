package com.telco.payment.application.event;

import java.math.BigDecimal;

/**
 * Outbox payload for {@code payment.completed.v1}.
 * Serialized to JSON by {@link com.telco.platform.outbox.OutboxService} (ADR-009, ADR-019).
 *
 * <p>{@code invoiceId} is nullable and backward-compatible (ADR-019): populated only when the
 * charge settles a specific invoice (Section 14.2 pay-invoice flow), letting billing-service's
 * {@code PaymentCompletedBillingConsumer} mark the invoice paid.
 */
public record PaymentCompletedEvent(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String invoiceId,
        String occurredAt
) {
}
