package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

/**
 * Terminates a subscription and releases its MSISDN back to the pool (FREE), emitting
 * {@code msisdn.released.v1} (FR-13, FR-14).
 *
 * <p>The REST entry point for this command is delivered by feature 9.3.
 */
public record TerminateSubscriptionCommand(
        UUID subscriptionId,
        String callerUserId,
        boolean callerIsAdmin
) implements Command<UUID> {
}
