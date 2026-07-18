package com.telco.fraud.application.dto;

import com.telco.fraud.domain.FraudCaseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/fraud-cases/{id}/resolve} (Feature 23.3.2). {@code status} is
 * the terminal outcome the agent is closing the case into and MUST be {@link FraudCaseStatus#CONFIRMED}
 * or {@link FraudCaseStatus#DISMISSED}; any other value (e.g. {@code OPEN}/{@code UNDER_REVIEW}) is
 * rejected as a business-rule violation by {@link com.telco.fraud.domain.FraudCase#resolve} (HTTP 422).
 * {@code note} is an optional free-text resolution note.
 */
public record ResolveFraudCaseRequest(
        @NotNull FraudCaseStatus status,
        @Size(max = 1000) String note
) {
}
