package com.telco.subscription.domain.mnp;

/**
 * Mobile Number Portability (MNP) port-in/port-out states (FR-16, POST-MVP, DEFERRED).
 *
 * <p>This enum reserves a clean extension point for number portability. It is intentionally NOT wired
 * into the MVP subscription flow, has no endpoint, and nothing in the MVP depends on it. The full
 * port lifecycle (donor/recipient messaging, NPC validation windows, scheduled cut-over) is out of
 * scope for the MVP and will be delivered post-MVP.
 *
 * <p>The canonical happy path is:
 * {@code REQUESTED -> VALIDATING -> APPROVED -> SCHEDULED -> PORTED};
 * either party may reject ({@code REJECTED}) or the request may be withdrawn ({@code CANCELLED}).
 */
public enum MnpPortState {

    /** A port request has been raised against a subscription/MSISDN. */
    REQUESTED,

    /** The request is being validated against the donor/recipient operator and number-portability rules. */
    VALIDATING,

    /** Validation passed; the port is approved and awaiting a cut-over slot. */
    APPROVED,

    /** A cut-over window has been scheduled. */
    SCHEDULED,

    /** The number has been ported and the subscription updated. Terminal. */
    PORTED,

    /** Validation or the counterparty rejected the request. Terminal. */
    REJECTED,

    /** The requester withdrew the port before completion. Terminal. */
    CANCELLED
}
