package com.telco.fraud.application.query;

import com.telco.fraud.application.dto.FraudCaseDetailResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Fetches a single {@link com.telco.fraud.domain.FraudCase} with its linked signals (Feature 23.3.1).
 * Returns 404 ({@code ResourceNotFoundException}) if no case has the given id.
 */
public record GetFraudCaseQuery(UUID id) implements Query<FraudCaseDetailResponse> {
}
