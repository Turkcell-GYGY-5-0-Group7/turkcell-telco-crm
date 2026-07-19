package com.telco.fraud.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Evaluates the {@code SUSPEND_REACTIVATE_VELOCITY} rule after a {@code SUBSCRIPTION_ACTIVATED}/
 * {@code SUBSCRIPTION_SUSPENDED} ingest (Feature 23.2.4): more than {@code thresholdCount}
 * suspend/reactivate transitions for the same {@code subscriptionId} inside the rule window raises one
 * signal at the rule's configured severity (ADR-029 Section 4 item 3). {@code NON_PAYMENT} suspensions
 * are excluded from the count (Amendment 3) so legitimate dunning cycles do not trip the rule.
 *
 * @param triggeringSignalId the id of the ingest row that triggered this evaluation
 */
public record EvaluateSuspendReactivateVelocityCommand(
        UUID triggeringSignalId,
        UUID customerId,
        UUID subscriptionId,
        Instant occurredAt
) implements Command<Void> {
}
