package com.telco.fraud.application.query;

import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Paged listing of {@link com.telco.fraud.domain.FraudCase}s (Feature 23.3.1), optionally filtered by
 * {@code status} and/or {@code customerId} (both nullable - a null filter is not applied). Returns a
 * {@link PageResult} of {@link FraudCaseSummaryResponse} (ADR-015).
 */
public record GetFraudCasesQuery(
        FraudCaseStatus status,
        UUID customerId,
        int page,
        int size
) implements Query<PageResult<FraudCaseSummaryResponse>> {
}
