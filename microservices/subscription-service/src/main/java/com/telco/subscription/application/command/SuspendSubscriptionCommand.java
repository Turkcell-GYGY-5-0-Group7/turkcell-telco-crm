package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

/**
 * Suspends a subscription (ACTIVE -> SUSPENDED), emitting {@code subscription.suspended.v1} (FR-14).
 *
 * <p>{@code reason} is carried into the event so downstream dunning/notification can template on it
 * (e.g. {@code NON_PAYMENT} for the payment-failed path, or an admin reason for the manual endpoint).
 */
public record SuspendSubscriptionCommand(
        UUID subscriptionId,
        String reason
) implements Command<UUID> {
}
