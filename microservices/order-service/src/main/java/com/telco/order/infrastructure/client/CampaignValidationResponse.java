package com.telco.order.infrastructure.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client-side DTO for campaign-service's {@code POST /internal/campaigns/validate} response body
 * (the unwrapped {@code ApiResult<CampaignValidationResponse>.data()}). Either eligible
 * ({@code discountType}/{@code discountValue} set, {@code reason} {@code null}) or ineligible
 * ({@code reason} set, discount fields {@code null}) - mirrors campaign-service's own
 * {@code EligibilityDecision}-derived response shape (ADR-027 Decision Section 4).
 */
public record CampaignValidationResponse(
        boolean eligible,
        UUID campaignId,
        String discountType,
        BigDecimal discountValue,
        String reason
) {
}
