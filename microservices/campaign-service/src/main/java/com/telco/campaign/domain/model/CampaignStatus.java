package com.telco.campaign.domain.model;

/**
 * Lifecycle status of a {@link Campaign} (design-note.md Section 5). State-transition behavior is
 * added in Feature 21.2 - this enum only names the states.
 */
public enum CampaignStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    EXPIRED,
    CANCELLED
}
