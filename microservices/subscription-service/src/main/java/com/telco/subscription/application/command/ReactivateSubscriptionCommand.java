package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

/**
 * Reactivates a suspended subscription (SUSPENDED -> ACTIVE), re-emitting {@code subscription.activated.v1}
 * (FR-14).
 *
 * <p>The event-catalog defines no dedicated {@code subscription.reactivated} event; reactivation
 * returns the subscription to the ACTIVE state, so the consistent, non-inventive choice is to
 * re-emit {@code subscription.activated.v1} (the subscription is once again active) rather than
 * introduce an uncataloged event.
 */
public record ReactivateSubscriptionCommand(
        UUID subscriptionId
) implements Command<UUID> {
}
