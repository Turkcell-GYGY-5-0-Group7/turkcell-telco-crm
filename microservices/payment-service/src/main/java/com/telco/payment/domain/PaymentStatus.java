package com.telco.payment.domain;

/** Lifecycle states of a {@link Payment} aggregate. */
public enum PaymentStatus {

    /** PSP charge not yet attempted (e.g., circuit breaker was open at creation time). */
    PENDING,

    /** PSP charge succeeded; money collected. */
    COMPLETED,

    /** PSP charge failed after all retry windows exhausted. */
    FAILED,

    /** A previously COMPLETED payment has been refunded. */
    REFUNDED
}
