package com.telco.dispute.application.event;

import java.math.BigDecimal;

/**
 * {@code dispute.resolved-customer.v1} (ADR-028 Section 6). The ONLY dispute-service event that
 * downstream consumers (billing-service Feature 22.4, payment-service Feature 22.5) may treat as
 * authorization for a real financial action. Carries both {@code invoiceId} and {@code paymentId}
 * (nullable each) so a consumer can determine which of billing-service/payment-service should act,
 * per ADR-028 Section 5's "depending on where the money currently sits" rule.
 */
public record DisputeResolvedCustomerEvent(
        String disputeId,
        String invoiceId,
        String paymentId,
        BigDecimal resolutionAmount,
        String resolvedAt
) {
}
