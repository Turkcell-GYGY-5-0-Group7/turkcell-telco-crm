package com.telco.payment.infrastructure.psp;

/**
 * Successful charge response from the PSP. Returned by {@link PspAdapter#charge} when the
 * payment was accepted. For failures the adapter throws {@link PspException}.
 *
 * @param transactionId PSP-assigned transaction reference
 */
public record ChargeResult(String transactionId) {
}
