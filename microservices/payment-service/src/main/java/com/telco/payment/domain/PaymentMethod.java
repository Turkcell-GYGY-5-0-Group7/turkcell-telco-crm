package com.telco.payment.domain;

/**
 * How the customer pays (FR-25): credit card, bank transfer, or wallet.
 *
 * <p>Label only in the MVP: the mock PSP ignores the method - every method flows through the same
 * charge path. Wallet balance modeling (the PDF's Wallet aggregate) is deliberately out of scope
 * per Sprint 24 design-note D6; {@link #WALLET} is a labeled method, not a balance-backed one.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER,
    WALLET
}
