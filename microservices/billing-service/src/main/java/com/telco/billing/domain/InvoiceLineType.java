package com.telco.billing.domain;

/**
 * Discriminates why an {@link InvoiceLine} exists. {@code ADJUSTMENT} lines are created exclusively
 * by {@link Invoice#applyDisputeAdjustment(java.math.BigDecimal)} in response to
 * {@code dispute.resolved-customer.v1} (ADR-028 Section 5); all other lines are {@code RECURRING}.
 */
public enum InvoiceLineType {
    RECURRING,
    ADJUSTMENT
}
