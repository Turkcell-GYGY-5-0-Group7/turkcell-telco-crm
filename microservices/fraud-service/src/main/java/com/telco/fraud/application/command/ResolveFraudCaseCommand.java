package com.telco.fraud.application.command;

import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/**
 * Resolves a {@link com.telco.fraud.domain.FraudCase} to a terminal {@code CONFIRMED}/{@code DISMISSED}
 * outcome (Feature 23.3.2, ADR-029 Section 5). Returns the resolved case summary.
 *
 * <p><strong>Detect-and-alert only (ADR-029 Section 5):</strong> the handler performs a status change
 * on the {@code FraudCase} row and publishes {@code fraud.case-resolved.v1} via the outbox - nothing
 * more. It NEVER calls subscription-service or any suspend/hold operation; any suspension is a
 * deliberate, manual agent action taken separately.
 *
 * @param caseId the case to resolve
 * @param status the terminal outcome ({@code CONFIRMED} or {@code DISMISSED})
 * @param note   optional free-text resolution note (audit only; not persisted on the case row)
 */
public record ResolveFraudCaseCommand(
        UUID caseId,
        FraudCaseStatus status,
        String note
) implements Command<FraudCaseSummaryResponse> {
}
