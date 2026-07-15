package com.telco.campaign.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outcome of {@code CampaignEligibilityService.evaluate(...)} (design-note.md Section 4, Feature
 * 21.2.3): either eligible (carrying the campaign id and the discount to apply) or ineligible
 * (carrying a specific {@link EligibilityReason} - never a bare boolean).
 */
public record EligibilityDecision(
        boolean eligible,
        UUID campaignId,
        DiscountType discountType,
        BigDecimal discountValue,
        EligibilityReason reason) {

    public static EligibilityDecision eligible(UUID campaignId, DiscountType discountType,
                                                BigDecimal discountValue) {
        return new EligibilityDecision(true, campaignId, discountType, discountValue, null);
    }

    /** Ineligible before a campaign could be resolved (e.g. {@link EligibilityReason#CAMPAIGN_NOT_FOUND}). */
    public static EligibilityDecision ineligible(EligibilityReason reason) {
        return new EligibilityDecision(false, null, null, null, reason);
    }

    /** Ineligible against a resolved campaign - the campaign id is still reported for traceability. */
    public static EligibilityDecision ineligible(UUID campaignId, EligibilityReason reason) {
        return new EligibilityDecision(false, campaignId, null, null, reason);
    }
}
