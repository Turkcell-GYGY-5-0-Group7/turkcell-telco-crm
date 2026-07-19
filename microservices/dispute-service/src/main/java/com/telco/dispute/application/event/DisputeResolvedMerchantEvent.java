package com.telco.dispute.application.event;

/**
 * {@code dispute.resolved-merchant.v1} (ADR-028 Section 6). Always a no-financial-change hold
 * release by contract, not by consumer-side omission.
 */
public record DisputeResolvedMerchantEvent(
        String disputeId,
        String invoiceId,
        String paymentId,
        String resolvedAt
) {
}
