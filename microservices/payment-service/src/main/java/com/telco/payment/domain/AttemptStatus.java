package com.telco.payment.domain;

/** Outcome of a single PSP charge attempt recorded in {@link PaymentAttempt}. */
public enum AttemptStatus {

    /** The PSP returned a successful charge response. */
    SUCCESS,

    /** The PSP returned an error or threw a technical exception. */
    FAILED
}
