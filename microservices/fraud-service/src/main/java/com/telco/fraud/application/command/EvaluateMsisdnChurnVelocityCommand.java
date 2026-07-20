package com.telco.fraud.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Evaluates the {@code MSISDN_CHURN_VELOCITY} rule after any {@code MSISDN_ALLOCATED}/
 * {@code MSISDN_RELEASED} ingest (Feature 23.2.3): more than {@code thresholdCount} allocate/release
 * signals for the same {@code customerId} inside the rolling window raises one MEDIUM-severity signal
 * per distinct breach (ADR-029 Section 4 item 2).
 *
 * <p>{@code customerId} is nullable: a release with no known prior allocation (ADR-029 Amendment 1)
 * carries no customer and is excluded from the velocity count - the handler no-ops on a null customer.
 *
 * @param triggeringSignalId the id of the ingest row that triggered this evaluation
 */
public record EvaluateMsisdnChurnVelocityCommand(
        UUID triggeringSignalId,
        UUID customerId,
        Instant occurredAt
) implements Command<Void> {
}
