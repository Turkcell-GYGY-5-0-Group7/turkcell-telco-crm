package com.telco.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /internal/campaigns/validate} (Feature 21.3.1, ADR-027 Decision
 * Section 4). {@code campaignCode} is optional: when omitted, the best-matching ACTIVE campaign for
 * {@code tariffCode} is auto-resolved (see {@code CampaignRepository
 * .findByStatusAndApplicableTariffCode}'s tie-break rule).
 */
public record CampaignValidationRequest(

        @NotNull
        UUID customerId,

        @NotBlank
        String tariffCode,

        String campaignCode

) {
}
