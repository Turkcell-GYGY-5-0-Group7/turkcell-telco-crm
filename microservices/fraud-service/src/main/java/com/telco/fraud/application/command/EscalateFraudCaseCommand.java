package com.telco.fraud.application.command;

import com.telco.fraud.domain.FraudSeverity;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/**
 * Escalates a just-raised {@link com.telco.fraud.domain.FraudSignal} into a
 * {@link com.telco.fraud.domain.FraudCase} (Feature 23.2.5). HIGH severity always opens a case on the
 * customer's first occurrence when none is {@code OPEN}/{@code UNDER_REVIEW}; MEDIUM/LOW escalate only
 * once the customer has more than one open-window signal. An existing {@code OPEN}/{@code UNDER_REVIEW}
 * case is attached to instead of opening a duplicate.
 *
 * <p>Detect-and-alert only (ADR-029 Section 5): the handler NEVER calls subscription-service or any
 * suspend/hold/mutate operation - the only downstream effect is publishing {@code fraud.case-opened.v1}.
 *
 * @param signalId the id of the {@link com.telco.fraud.domain.FraudSignal} that triggered escalation
 * @param severity that signal's severity, driving the HIGH-always vs. MEDIUM/LOW-repeated decision
 */
public record EscalateFraudCaseCommand(
        UUID signalId,
        UUID customerId,
        FraudSeverity severity
) implements Command<Void> {
}
