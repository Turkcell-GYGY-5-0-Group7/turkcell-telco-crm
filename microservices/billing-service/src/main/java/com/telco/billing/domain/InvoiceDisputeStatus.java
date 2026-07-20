package com.telco.billing.domain;

/**
 * Dispute hold state of an {@link Invoice} (ADR-028 Section 5). A hold flag only - it never mutates
 * {@code subTotal}/{@code grandTotal}/{@code tax}/{@code status}; it only excludes the invoice from
 * the overdue/dunning check while {@code ON_HOLD}.
 */
public enum InvoiceDisputeStatus {
    NONE,
    ON_HOLD
}
