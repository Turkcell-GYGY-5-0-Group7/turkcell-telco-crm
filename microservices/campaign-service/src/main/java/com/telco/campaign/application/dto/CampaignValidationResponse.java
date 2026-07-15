package com.telco.campaign.application.dto;

import com.telco.campaign.domain.model.EligibilityDecision;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response body for {@code POST /internal/campaigns/validate} (Feature 21.3.1). Either
 * {@code eligible: true} with a non-null {@code discountType}/{@code discountValue}, or
 * {@code eligible: false} with a specific {@code reason} - never a bare boolean, mirroring
 * {@link EligibilityDecision} (ADR-027 Decision Section 4). {@code reason} is {@code null} when
 * eligible.
 */
public record CampaignValidationResponse(
        boolean eligible,
        UUID campaignId,
        String discountType,
        BigDecimal discountValue,
        String reason
) {

    public static CampaignValidationResponse from(EligibilityDecision decision) {
        return new CampaignValidationResponse(
                decision.eligible(),
                decision.campaignId(),
                decision.discountType() == null ? null : decision.discountType().name(),
                decision.discountValue(),
                decision.reason() == null ? null : decision.reason().name()
        );
    }
}
