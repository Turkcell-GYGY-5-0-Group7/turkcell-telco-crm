package com.telco.payment.domain;

/**
 * How a payment is settled (FR-25). All methods route through the same mock PSP in the MVP;
 * the method is captured for reporting and future PSP routing, not differential processing.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER,
    WALLET
}
