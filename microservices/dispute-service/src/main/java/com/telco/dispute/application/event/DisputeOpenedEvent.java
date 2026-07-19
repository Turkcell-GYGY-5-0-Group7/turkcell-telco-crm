package com.telco.dispute.application.event;

import java.math.BigDecimal;

/**
 * {@code dispute.opened.v1} (ADR-028 Section 6). The ONLY signal a provisional hold ever travels
 * on - never a financial instruction (ADR-028 Section 5).
 */
public record DisputeOpenedEvent(
        String disputeId,
        String invoiceId,
        String paymentId,
        String customerId,
        BigDecimal disputedAmount,
        String reasonCode,
        String openedAt
) {
}
