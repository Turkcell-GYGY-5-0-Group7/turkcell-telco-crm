package com.telco.subscription.domain.mnp;

/**
 * Mobile Number Portability (MNP) port state machine (FR-16, POST-MVP, DEFERRED).
 *
 * <p>Defines the transition surface for a number-portability port without any implementation. This is
 * a reserved extension point only: there is no implementing bean, no command/handler, no endpoint,
 * and no MVP flow depends on it. It exists so the post-MVP MNP feature can slot in against a stable
 * contract.
 *
 * <p>Intended transitions (enforced by a future implementation):
 * <ul>
 *   <li>{@code REQUESTED -> VALIDATING}</li>
 *   <li>{@code VALIDATING -> APPROVED | REJECTED}</li>
 *   <li>{@code APPROVED -> SCHEDULED}</li>
 *   <li>{@code SCHEDULED -> PORTED}</li>
 *   <li>any non-terminal state {@code -> CANCELLED}</li>
 * </ul>
 * Illegal transitions are expected to raise a domain rule exception, consistent with the
 * {@link com.telco.subscription.domain.Subscription} state machine.
 */
public interface MnpPortStateMachine {

    /** Begins a port request: {@code (none) -> REQUESTED}. */
    MnpPortState request();

    /** {@code REQUESTED -> VALIDATING}. */
    MnpPortState validate();

    /** {@code VALIDATING -> APPROVED}. */
    MnpPortState approve();

    /** {@code VALIDATING -> REJECTED} (terminal). */
    MnpPortState reject(String reason);

    /** {@code APPROVED -> SCHEDULED}. */
    MnpPortState schedule();

    /** {@code SCHEDULED -> PORTED} (terminal). */
    MnpPortState complete();

    /** Any non-terminal state {@code -> CANCELLED} (terminal). */
    MnpPortState cancel(String reason);

    /** @return the current port state. */
    MnpPortState currentState();
}
