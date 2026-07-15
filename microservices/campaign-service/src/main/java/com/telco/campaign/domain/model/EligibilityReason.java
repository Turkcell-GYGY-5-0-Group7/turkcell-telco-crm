package com.telco.campaign.domain.model;

/**
 * Distinguishable reason code carried by an ineligible {@link EligibilityDecision} (design-note.md
 * Section 4, Feature 21.2.3). An ineligible outcome always carries one of these - never a bare
 * boolean.
 */
public enum EligibilityReason {

    /** No campaign exists for the code the caller supplied. */
    CAMPAIGN_NOT_FOUND,

    /** The campaign's {@code validTo} has passed (validity-window check). */
    EXPIRED,

    /** The campaign's {@code validFrom} is still in the future (validity-window check). */
    NOT_YET_ACTIVE,

    /** The campaign exists but is not in {@link CampaignStatus#ACTIVE} (DRAFT/PAUSED/CANCELLED). */
    NOT_ACTIVE_STATUS,

    /** The tariff code is not present in the campaign's {@code applicableTariffCodes}. */
    TARIFF_NOT_APPLICABLE,

    /** The customer has already reached {@code perCustomerRedemptionCap} for this campaign. */
    PER_CUSTOMER_CAP_EXCEEDED,

    /** The campaign has already reached its {@code totalRedemptionCap} across all customers. */
    TOTAL_CAP_EXCEEDED,

    /**
     * No {@code campaignCode} was supplied and no ACTIVE campaign's {@code applicableTariffCodes}
     * includes the given tariff code either (Feature 21.3.1's auto-resolution path - distinct from
     * {@link #CAMPAIGN_NOT_FOUND}, which means an explicitly-supplied code did not resolve to any
     * campaign at all).
     */
    NO_MATCHING_CAMPAIGN
}
