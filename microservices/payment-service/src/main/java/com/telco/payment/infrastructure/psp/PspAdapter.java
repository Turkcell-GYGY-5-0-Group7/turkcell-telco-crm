package com.telco.payment.infrastructure.psp;

import java.math.BigDecimal;

/**
 * Port for the payment service provider (PSP). Implementations must be stateless and thread-safe.
 * The production bean registered by {@code PspConfig} wraps the implementation with a
 * Resilience4j circuit breaker.
 */
public interface PspAdapter {

    /**
     * Attempts to charge the given amount against the customer.
     *
     * @param paymentRequestId idempotency key forwarded to the PSP
     * @param amount           amount to charge (positive, non-null)
     * @param customerId       identifier of the paying customer
     * @return {@link ChargeResult} on success
     * @throws PspException when the PSP signals a technical or business-rule failure
     */
    ChargeResult charge(String paymentRequestId, BigDecimal amount, String customerId)
            throws PspException;

    /**
     * Attempts to refund a previously charged payment.
     *
     * @param paymentRequestId original idempotency key used at charge time
     * @param amount           amount to refund
     * @param customerId       identifier of the customer
     * @throws PspException when the PSP signals a failure
     */
    ChargeResult refund(String paymentRequestId, BigDecimal amount, String customerId)
            throws PspException;
}
