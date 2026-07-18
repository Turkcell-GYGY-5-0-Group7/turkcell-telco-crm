package com.telco.fraud.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Evaluates the {@code RAPID_SIM_SWAP} rule for a just-ingested {@code MSISDN_ALLOCATED} signal
 * (Feature 23.2.2): a prior {@code MSISDN_RELEASED} for the same MSISDN, reassigned to a DIFFERENT
 * {@code subscriptionId}, inside the rule window raises a HIGH-severity signal (ADR-029 Section 4
 * item 1, Amendment 2 - {@code subscriptionId} is the observable re-assignment key; no SimCard/ICCID
 * exists on these events).
 *
 * @param allocatedSignalId the id of the {@code MSISDN_ALLOCATED} row that triggered this evaluation
 */
public record EvaluateRapidSimSwapCommand(
        UUID allocatedSignalId,
        UUID customerId,
        String msisdn,
        UUID subscriptionId,
        Instant occurredAt
) implements Command<Void> {
}
