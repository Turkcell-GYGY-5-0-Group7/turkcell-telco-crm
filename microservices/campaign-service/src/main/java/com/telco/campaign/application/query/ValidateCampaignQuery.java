package com.telco.campaign.application.query;

import com.telco.campaign.application.dto.CampaignValidationResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Resolves the eligibility/discount decision for {@code customerId} against {@code tariffCode}
 * (Feature 21.3.1). {@code campaignCode} is optional; when {@code null}/blank, the handler
 * auto-resolves the best-matching ACTIVE campaign for {@code tariffCode} before evaluating.
 * Read-only: never creates or mutates a {@code CampaignRedemption} row.
 */
public record ValidateCampaignQuery(
        UUID customerId,
        String tariffCode,
        String campaignCode
) implements Query<CampaignValidationResponse> {
}
